package com.salihraphics.meow.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salihraphics.meow.analysis.layout.PdfPageLayoutSource
import com.salihraphics.meow.analysis.text.MlKitPageOcr
import com.salihraphics.meow.feature.answerkey.AnswerKeyRoute
import com.salihraphics.meow.feature.answerkey.AnswerKeyViewModel
import com.salihraphics.meow.feature.library.LibraryRoute
import com.salihraphics.meow.feature.library.LibraryViewModel
import com.salihraphics.meow.feature.library.fileThumbnailLoader
import com.salihraphics.meow.feature.viewer.PdfViewerRoute
import com.salihraphics.meow.feature.viewer.PdfViewerViewModel
import kotlinx.coroutines.flow.map
import com.salihraphics.meow.MeowApplication
import com.salihraphics.meow.domain.model.UserPreferences
import com.salihraphics.meow.feature.home.HomeScreen
import com.salihraphics.meow.feature.onboarding.OnboardingRoute
import com.salihraphics.meow.feature.onboarding.OnboardingViewModel
import com.salihraphics.meow.feature.settings.SettingsRoute
import com.salihraphics.meow.feature.settings.SettingsViewModel

object MeowDestinations {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LIBRARY = "library"
    const val VIEWER = "viewer/{documentId}"
    const val ANSWER_KEY = "answerkey/{documentId}"
    fun answerKey(documentId: String) = "answerkey/$documentId"
    fun viewer(documentId: String) = "viewer/$documentId"
}

@Composable
fun MeowNavHost(
    preferences: UserPreferences,
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val container = (LocalContext.current.applicationContext as MeowApplication).container
    val repository = container.userPreferencesRepository

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(MeowDestinations.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = viewModelFactory { initializer { OnboardingViewModel(createSavedStateHandle(), repository) } },
            )
            OnboardingRoute(vm, onCompleted = { navController.replaceAllWith(MeowDestinations.HOME) })
        }
        composable(MeowDestinations.HOME) {
            val libraryCount by remember { container.pdfLibraryRepository.documents.map { it.size } }
                .collectAsStateWithLifecycle(initialValue = null)
            HomeScreen(
                profile = preferences.profile,
                onOpenSettings = { navController.navigate(MeowDestinations.SETTINGS) { launchSingleTop = true } },
                onOpenLibrary = { navController.navigate(MeowDestinations.LIBRARY) { launchSingleTop = true } },
                libraryCount = libraryCount,
            )
        }
        composable(MeowDestinations.LIBRARY) {
            val library = container.pdfLibraryRepository
            val vm: LibraryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LibraryViewModel(library, container.answerKeyRepository.observeStatuses(container.answerKeyScheduler.progress))
                    }
                },
            )
            LibraryRoute(
                viewModel = vm,
                thumbnails = remember { fileThumbnailLoader(library::thumbnailFile) },
                onOpen = { navController.navigate(MeowDestinations.viewer(it.id)) { launchSingleTop = true } },
                onAnswerKey = { navController.navigate(MeowDestinations.answerKey(it.id)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            MeowDestinations.ANSWER_KEY,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
            val documentId = requireNotNull(entry.arguments?.getString("documentId"))
            val vm: AnswerKeyViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        AnswerKeyViewModel(
                            documentId = documentId,
                            documentName = container.pdfLibraryRepository.documents.map { list ->
                                list.firstOrNull { it.id == documentId }.let { (it?.displayName ?: "") to (it?.pageCount ?: 0) }
                            },
                            keys = container.answerKeyRepository,
                            progress = container.answerKeyScheduler.progress,
                            searchAgainAction = { container.answerKeyScheduler.enqueue(documentId, force = true) },
                            requests = container.answerKeyScheduler.requests,
                            readKeyPagesAction = { pages -> container.answerKeyScheduler.readKeyPages(documentId, pages) },
                            readKeyPdfAction = { uri -> container.answerKeyScheduler.readKeyPdf(documentId, uri) },
                            clearRequestAction = { container.answerKeyScheduler.clearRequest(documentId) },
                        )
                    }
                },
            )
            AnswerKeyRoute(
                vm,
                onBack = { navController.popBackStack() },
                pagePreview = { sourceId, pageIndex ->
                    val file = if (sourceId == documentId) container.pdfLibraryRepository.pdfFile(documentId) else container.keySourceFiles.file(sourceId)
                    val session = container.pdfRenderSessionFactory.open(file)
                    try {
                        session.render(pageIndex, PREVIEW_WIDTH_PX).asImageBitmap()
                    } finally {
                        session.close()
                    }
                },
                loadReport = { container.analysisReports.load(documentId) },
            )
        }
        composable(
            MeowDestinations.VIEWER,
            arguments = listOf(navArgument("documentId") { type = NavType.StringType }),
        ) { entry ->
            val documentId = requireNotNull(entry.arguments?.getString("documentId"))
            val vm: PdfViewerViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        PdfViewerViewModel(
                            documentId = documentId,
                            savedState = createSavedStateHandle(),
                            repository = container.pdfLibraryRepository,
                            sessions = container.pdfRenderSessionFactory,
                            annotationRepository = container.annotationRepository,
                            applicationScope = container.applicationScope,
                            answerKeys = container.answerKeyRepository,
                            layoutSourceFactory = { file, session ->
                                PdfPageLayoutSource(file, container.pdfTextSource, MlKitPageOcr(session))
                            },
                            immediateFeedbackSetting = repository.preferences.map { it.immediateFeedback },
                        )
                    }
                },
            )
            PdfViewerRoute(
                vm,
                onBack = { navController.popBackStack() },
                onOpenAnswerKey = { navController.navigate(MeowDestinations.answerKey(documentId)) { launchSingleTop = true } },
                studyTimer = container.studyTimer,
            )
        }
        composable(MeowDestinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repository, container.accountRepository) } })
            SettingsRoute(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onRestartOnboarding = { navController.replaceAllWith(MeowDestinations.ONBOARDING) },
            )
        }
    }
}

private const val PREVIEW_WIDTH_PX = 1200

/** Navigates to [route] and clears the back stack, so Back never returns to a finished flow. */
private fun NavHostController.replaceAllWith(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
