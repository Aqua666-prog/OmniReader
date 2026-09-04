package com.sergey.reader.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sergey.reader.data.db.BookEntity
import java.text.DecimalFormat

@Composable
fun CoverImage(book: BookEntity, modifier: Modifier = Modifier) {
    val bitmap = remember(book.coverPath) {
        book.coverPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Обложка ${book.title}",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
fun BookListCard(
    book: BookEntity,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
    onWant: () -> Unit,
    onFinished: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(10.dp)) {
            CoverImage(book, Modifier.width(92.dp).aspectRatio(0.67f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!book.series.isNullOrBlank()) {
                    Text(
                        buildString {
                            append(book.series)
                            book.seriesIndex?.let { append(" · том ${formatIndex(it)}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("${book.format} · ${formatSize(book.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onFavorite) {
                        Icon(if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Избранное")
                    }
                    IconButton(onClick = onWant) {
                        Icon(Icons.Default.Schedule, contentDescription = "Хочу прочитать", tint = if (book.wantToRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onFinished) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Прочитано", tint = if (book.finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDetails) { Icon(Icons.Default.Info, contentDescription = "О книге") }
                }
            }
        }
    }
}

@Composable
fun BookGridCard(book: BookEntity, onOpen: () -> Unit, onDetails: () -> Unit) {
    Card(modifier = Modifier.padding(6.dp).clickable(onClick = onOpen)) {
        Column {
            CoverImage(book, Modifier.fillMaxWidth().aspectRatio(0.67f))
            Column(Modifier.padding(8.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { book.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDetails, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Info, contentDescription = "О книге", modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "?"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) "${DecimalFormat("0.#").format(mb)} МБ" else "${bytes / 1024} КБ"
}

private fun formatIndex(index: Double): String = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
