package com.sergey.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.sergey.reader.ui.ReaderApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ReaderApplication).container
        if (savedInstanceState == null) handleIncomingIntent(intent, container)
        setContent {
            ReaderApp(container)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent, (application as ReaderApplication).container)
    }

    private fun handleIncomingIntent(intent: Intent?, container: AppContainer) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        lifecycleScope.launch {
            container.books.importDocument(uri)
                .onSuccess { container.pendingOpenBook.value = it }
                .onFailure { android.widget.Toast.makeText(this@MainActivity, it.message ?: "Не удалось открыть файл", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
}
