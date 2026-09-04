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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            CoverImage(book, Modifier.width(100.dp).aspectRatio(0.67f).clip(RoundedCornerShape(14.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    FormatBadge(book.format)
                }
                if (book.authors.isNotBlank()) {
                    Text(book.authors, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!book.series.isNullOrBlank()) {
                    Text(
                        buildString {
                            append(book.series)
                            book.seriesIndex?.let { append(" · том ${formatIndex(it)}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(formatSize(book.sizeBytes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp))
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
                        Icon(if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Избранное", modifier = Modifier.size(20.dp), tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onWant, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = "Хочу прочитать", modifier = Modifier.size(20.dp), tint = if (book.wantToRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onFinished, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Прочитано", modifier = Modifier.size(20.dp), tint = if (book.finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDetails, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "О книге", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BookGridCard(book: BookEntity, onOpen: () -> Unit, onDetails: () -> Unit) {
    Card(
        modifier = Modifier.padding(7.dp).clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                CoverImage(book, Modifier.fillMaxWidth().aspectRatio(0.67f))
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                ) {
                    Text(book.format, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.authors.isNotBlank()) {
                    Text(book.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDetails, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "О книге", modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(format: String) {
    Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(format, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "Размер неизвестен"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) "${DecimalFormat("0.#").format(mb)} МБ" else "${bytes / 1024} КБ"
}

private fun formatIndex(index: Double): String = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
