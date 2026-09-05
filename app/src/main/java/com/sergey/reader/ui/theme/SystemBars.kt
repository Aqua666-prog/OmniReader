package com.sergey.reader.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat

@Suppress("DEPRECATION")
@Composable
fun SystemBars(background: Color, darkIcons: Boolean) {
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    DisposableEffect(activity, view, background, darkIcons) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val status = window?.statusBarColor
        val navigation = window?.navigationBarColor
        val statusIcons = controller?.isAppearanceLightStatusBars
        val navigationIcons = controller?.isAppearanceLightNavigationBars
        if (window != null && controller != null) {
            window.statusBarColor = background.toArgb()
            window.navigationBarColor = background.toArgb()
            controller.isAppearanceLightStatusBars = darkIcons
            controller.isAppearanceLightNavigationBars = darkIcons
        }
        onDispose {
            if (window != null && controller != null) {
                status?.let { window.statusBarColor = it }
                navigation?.let { window.navigationBarColor = it }
                statusIcons?.let { controller.isAppearanceLightStatusBars = it }
                navigationIcons?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findActivity() else null
    else -> null
}
