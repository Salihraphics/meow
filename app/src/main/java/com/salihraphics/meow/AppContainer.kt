package com.salihraphics.meow

import android.content.Context
import com.salihraphics.meow.data.account.AccountRepository
import com.salihraphics.meow.data.account.LocalOnlyAccountRepository
import com.salihraphics.meow.data.annotation.AnnotationRepository
import com.salihraphics.meow.data.annotation.LocalAnnotationRepository
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency wiring. Small on purpose; swap for Hilt only if it starts to hurt. */
class AppContainer(context: Context) {
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
        )
    }

    val annotationRepository: AnnotationRepository by lazy {
        LocalAnnotationRepository(database.strokes(), applicationScope)
    }

    val pdfRenderSessionFactory: PdfRenderSessionFactory = AndroidPdfRenderSession.Factory

    init {
        // No import can be running yet, so anything left in "incoming" is from an interrupted one.
        applicationScope.launch(Dispatchers.IO) { pdfFiles.clearIncoming() }
    }
}
