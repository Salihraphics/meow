package com.salihraphics.meow.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            val vm: LibraryViewModel = viewModel(factory = viewModelFactory { initializer { LibraryViewModel(library) } })
            LibraryRoute(
                viewModel = vm,
                thumbnails = remember { fileThumbnailLoader(library::thumbnailFile) },
                onOpen = { navController.navigate(MeowDestinations.viewer(it.id)) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
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
                        )
                    }
                },
            )
            PdfViewerRoute(vm, onBack = { navController.popBackStack() })
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

/** Navigates to [route] and clears the back stack, so Back never returns to a finished flow. */
private fun NavHostController.replaceAllWith(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
