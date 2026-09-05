package com.sergey.reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/** Window-local changes are restored when leaving the reader. No system permission needed. */
@Composable
fun ReadingComfort(keepScreenOn: Boolean, brightness: Float) {
    val view = LocalView.current
    val activity = LocalContext.current.activity()
    DisposableEffect(view, keepScreenOn) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(activity, brightness) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        if (window != null) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        onDispose {
            if (window != null && previous != null) {
                window.attributes = window.attributes.apply { screenBrightness = previous }
            }
        }
    }
}
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.activity() else null
    else -> null
}
