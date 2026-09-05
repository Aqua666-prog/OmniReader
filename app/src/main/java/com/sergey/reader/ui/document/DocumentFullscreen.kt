package com.sergey.reader.ui.document

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
internal fun DocumentFullscreen(hidden: Boolean) {
    val activity=LocalContext.current.documentActivity()
    DisposableEffect(activity,hidden) {
        val window=activity?.window
        val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)}
        val previous=controller?.systemBarsBehavior
        if(hidden) {
            controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if(previous!=null) controller?.systemBarsBehavior=previous
        }
    }
}
private tailrec fun Context.documentActivity(): Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> if(baseContext!==this) baseContext.documentActivity() else null
    else -> null
}
