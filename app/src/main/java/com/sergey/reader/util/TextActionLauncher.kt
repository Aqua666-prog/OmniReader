package com.sergey.reader.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TextActionLauncher {
    fun openUrlTemplate(context: Context, template: String, text: String): Result<Unit> = runCatching {
        val encoded = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8.toString())
        val url = template.replace("{text}", encoded)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun shareText(context: Context, text: String, title: String? = null): Result<Unit> = runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
