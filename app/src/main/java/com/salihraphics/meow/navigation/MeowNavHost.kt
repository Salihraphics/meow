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
}

@Composable
fun MeowNavHost(
    preferences: UserPreferences,
    startDestination: String,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val repository = (LocalContext.current.applicationContext as MeowApplication).container.userPreferencesRepository

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
            HomeScreen(
                profile = preferences.profile,
                onOpenSettings = { navController.navigate(MeowDestinations.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(MeowDestinations.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(repository) } })
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
