package com.salihraphics.meow

import android.content.Context
import com.salihraphics.meow.analysis.AnswerKeyAnalysisScheduler
import com.salihraphics.meow.analysis.AnswerKeyAnalyzer
import com.salihraphics.meow.analysis.text.MlKitPageOcr
import com.salihraphics.meow.analysis.text.RenderedPageImages
import com.salihraphics.meow.analysis.text.PdfBoxTextSource
import com.salihraphics.meow.analysis.text.PdfTextSource
import com.salihraphics.meow.data.account.AccountRepository
import com.salihraphics.meow.data.answerkey.AnswerKeyRepository
import com.salihraphics.meow.data.account.LocalOnlyAccountRepository
import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.data.annotation.LocalAnnotationRepository
import com.salihraphics.meow.data.answerkey.AnalysisReports
import com.salihraphics.meow.data.answerkey.KeySourceFiles
import com.salihraphics.meow.data.library.ContentResolverDocumentSource
import com.salihraphics.meow.data.library.LocalPdfLibraryRepository
import com.salihraphics.meow.data.library.PdfFileStore
import com.salihraphics.meow.data.library.PdfLibraryRepository
import com.salihraphics.meow.data.library.db.MeowDatabase
import com.salihraphics.meow.data.pdf.AndroidPdfInspector
import com.salihraphics.meow.data.pdf.AndroidPdfRenderSession
import com.salihraphics.meow.data.pdf.PdfRenderSessionFactory
import com.salihraphics.meow.data.preferences.DataStoreUserPreferencesRepository
import com.salihraphics.meow.data.preferences.UserPreferencesRepository
import com.salihraphics.meow.feature.study.StudyTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency wiring. Small on purpose; swap for Hilt only if it starts to hurt. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For short writes that must outlive a screen, such as saving the last-read page on exit. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val userPreferencesRepository: UserPreferencesRepository =
        DataStoreUserPreferencesRepository(context)

    val accountRepository: AccountRepository = LocalOnlyAccountRepository()

    private val database by lazy { MeowDatabase.create(context) }

    private val pdfFiles = PdfFileStore(File(context.filesDir, "library"))

    val pdfLibraryRepository: PdfLibraryRepository by lazy {
        LocalPdfLibraryRepository(
            dao = database.pdfDocuments(),
            files = pdfFiles,
            source = ContentResolverDocumentSource(context.contentResolver),
            inspector = AndroidPdfInspector(),
            strokes = database.strokes(),
            onRemoved = { id ->
                answerKeyRepository.removeDocument(id)
                analysisReports.delete(id)
            },
        )
    }

    val answerKeyRepository: AnswerKeyRepository by lazy { AnswerKeyRepository(database) }

    /** The latest answer-key search report per document, for "Tanılama raporu". */
    val analysisReports = AnalysisReports(File(context.filesDir, "keyreports"))

    /** Separate answer-key PDFs students add for their books. */
    val keySourceFiles = KeySourceFiles(File(context.filesDir, "keysources"), ContentResolverDocumentSource(context.contentResolver))

    /** Searches every library PDF for answer keys in the background; see [AnswerKeyAnalyzer]. */
    val answerKeyScheduler: AnswerKeyAnalysisScheduler by lazy {
        AnswerKeyAnalysisScheduler(
            scope = applicationScope,
            library = pdfLibraryRepository,
            keys = answerKeyRepository,
            analyzer = AnswerKeyAnalyzer(PdfBoxTextSource(appContext)),
            ocrFor = { file ->
                val session = pdfRenderSessionFactory.open(file)
                object : AnswerKeyAnalysisScheduler.OcrHandle {
                    override val ocr = MlKitPageOcr(session)
                    override val images = RenderedPageImages(session)
                    override fun close() = session.close()
                }
            },
            keyFiles = keySourceFiles,
            reports = analysisReports,
        )
    }

    /** Positioned text of single pages, for recognising answers marked in the viewer. */
    val pdfTextSource: PdfTextSource by lazy { PdfBoxTextSource(appContext) }

    val annotationRepository: AnnotationRepository by lazy {
        LocalAnnotationRepository(database.strokes(), applicationScope)
    }

    val pdfRenderSessionFactory: PdfRenderSessionFactory = AndroidPdfRenderSession.Factory

    /** One study timer for the whole app; keeps running while the student moves between PDFs. */
    val studyTimer: StudyTimerController by lazy { StudyTimerController(database.study(), applicationScope) }

    init {
        // No import can be running yet, so anything left in "incoming" is from an interrupted one.
        applicationScope.launch(Dispatchers.IO) { pdfFiles.clearIncoming() }
    }
}
