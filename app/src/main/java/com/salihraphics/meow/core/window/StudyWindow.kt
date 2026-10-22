package com.salihraphics.meow.core.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The window while studying a PDF: immersive (status and navigation bars hidden, revealed for a
 * moment by the system swipe, then hidden again) and kept awake. Only the study workspace does
 * this; leaving it shows the bars again and gives the screen back its normal timeout.
 *
 * The app draws edge to edge everywhere (`enableEdgeToEdge`); screens pad themselves with
 * `WindowInsets.safeDrawing`. Hidden bars have no insets, so the workspace gets the whole
 * screen with no empty strip, and only a display cutout is still kept clear.
 */
object StudyWindow {
    fun enter(window: Window, view: View) {
        // A window flag, not a wake lock: it only holds while this window is visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars(window, view)
    }

    fun hideBars(window: Window, view: View) {
        WindowCompat.getInsetsController(window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun leave(window: Window, view: View) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
    }
}

/** True inside the study workspace, so dialogs opened from it stay immersive too. */
val LocalStudyWindow = staticCompositionLocalOf { false }

/**
 * Applies [StudyWindow] while [content] is on screen. The bars are hidden again whenever the
 * window regains focus (after a dialog, the notification shade, another app) and on every
 * resume, since some Android versions bring them back then.
 */
@Composable
fun StudyWindowEffect(content: @Composable () -> Unit) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (window != null && !LocalInspectionMode.current) {
        DisposableEffect(window, view, lifecycle) {
            StudyWindow.enter(window, view)
            val onResume = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) StudyWindow.hideBars(window, view)
            }
            val onFocus = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) StudyWindow.hideBars(window, view)
            }
            lifecycle.addObserver(onResume)
            view.viewTreeObserver.addOnWindowFocusChangeListener(onFocus)
            onDispose {
                lifecycle.removeObserver(onResume)
                view.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(onFocus)
                StudyWindow.leave(window, view)
            }
        }
    }
    CompositionLocalProvider(LocalStudyWindow provides true, content = content)
}

/**
 * Call inside a dialog's content. A dialog is a window of its own, and while it has focus its
 * own settings decide whether the bars show; opened from the study workspace, it hides them too.
 */
@Composable
fun ImmersiveDialogWindow() {
    if (!LocalStudyWindow.current || LocalInspectionMode.current) return
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window, view) {
        StudyWindow.hideBars(window, view)
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
