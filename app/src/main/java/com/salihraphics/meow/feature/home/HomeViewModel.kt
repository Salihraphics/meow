package com.salihraphics.meow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salihraphics.meow.data.dashboard.DashboardRepository
import com.salihraphics.meow.data.dashboard.NoteSummary
import com.salihraphics.meow.domain.model.ExamGoal
import com.salihraphics.meow.domain.model.LibraryFolder
import com.salihraphics.meow.domain.model.PdfDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.TimeZone

/** A note with the name of the PDF it is in. */
data class HomeNote(val note: NoteSummary, val documentName: String)

data class HomeDocuments(
    /** The PDF to continue: the one opened last, or the newest one if none was opened yet. */
    val continueWith: PdfDocument? = null,
    val pinned: List<PdfDocument> = emptyList(),
    /** Recently opened, without [continueWith]. */
    val recent: List<PdfDocument> = emptyList(),
    val count: Int = 0,
    val folderNames: Map<String, String> = emptyMap(),
)

class HomeViewModel(
    documents: Flow<List<PdfDocument>>,
    folders: Flow<List<LibraryFolder>>,
    dashboard: DashboardRepository,
    examGoal: Flow<ExamGoal?>,
    private val saveExamGoal: suspend (ExamGoal?) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault,
) : ViewModel() {

    /** Null while loading. */
    val documents: StateFlow<HomeDocuments?> = combine(documents, folders) { docs, folderList ->
        arrange(docs, folderList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val notes: StateFlow<List<HomeNote>> = combine(dashboard.recentNotes(MAX_NOTES), documents) { notes, docs ->
        val names = docs.associate { it.id to it.displayName }
        notes.mapNotNull { note -> names[note.documentId]?.let { HomeNote(note, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The clock, once a minute: enough to roll the summary over at midnight and on Mondays. */
    private val minutes: Flow<Long> = flow {
        while (true) {
            emit(now())
            delay(MINUTE - now() % MINUTE)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val summary: StateFlow<StudySummary?> = minutes
        .map { StudySummary.startOfWeek(it, zone()) }
        .distinctUntilChanged()
        .flatMapLatest { week ->
            combine(dashboard.studySince(week), dashboard.checkedTestsSince(week), minutes) { study, tests, at ->
                StudySummary.of(study, tests, at, zone())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val examGoal: StateFlow<ExamGoal?> = examGoal.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setExamGoal(goal: ExamGoal?) {
        viewModelScope.launch { saveExamGoal(goal) }
    }

    companion object {
        const val MAX_NOTES = 5
        const val MAX_RECENT = 4
        private const val MINUTE = 60_000L

        fun arrange(docs: List<PdfDocument>, folders: List<LibraryFolder> = emptyList()): HomeDocuments {
            val opened = docs.filter { it.lastOpenedAt != null }.sortedByDescending { it.lastOpenedAt }
            val continueWith = opened.firstOrNull() ?: docs.maxByOrNull { it.importedAt }
            return HomeDocuments(
                continueWith = continueWith,
                pinned = docs.filter { it.pinned }.sortedByDescending { it.lastOpenedAt ?: it.importedAt },
                recent = opened.filter { it.id != continueWith?.id }.take(MAX_RECENT),
                count = docs.size,
                folderNames = folders.associate { it.id to it.name },
            )
        }
    }
}
