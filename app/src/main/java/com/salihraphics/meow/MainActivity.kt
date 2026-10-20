package com.salihraphics.meow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.salihraphics.meow.core.designsystem.theme.MeowTheme
import com.salihraphics.meow.domain.model.ThemeMode
import com.salihraphics.meow.navigation.MeowDestinations
import com.salihraphics.meow.navigation.MeowNavHost

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer { MainViewModel((application as MeowApplication).container.userPreferencesRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No artificial delay: the splash only covers reading local preferences.
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = state as? MainUiState.Ready ?: return@setContent
            val prefs = ready.preferences
            val dark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Decided once per launch; later preference changes navigate explicitly.
            val start = remember {
                if (prefs.onboardingCompleted) MeowDestinations.HOME else MeowDestinations.ONBOARDING
            }
            MeowTheme(darkTheme = dark, mascotAnimationsEnabled = prefs.mascotAnimationsEnabled) {
                // Surface also provides the content colour icons and text inherit.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MeowNavHost(preferences = prefs, startDestination = start)
                }
            }
        }
    }
}
