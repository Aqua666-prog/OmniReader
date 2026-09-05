package com.sergey.reader.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sergey.reader.data.db.BookEntity

private val covers = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}
private val coverColors = listOf(0xFF315A4B, 0xFF5B496B, 0xFF80513D, 0xFF355D70, 0xFF6D603C, 0xFF734C55)

@Composable
fun CoverImage(book: BookEntity, modifier: Modifier = Modifier) {
    // Decode bounded thumbnails off the UI thread; the cache budget is bytes, not book count.
    val bitmap by produceState<Bitmap?>(null, book.coverPath) {
        value = null
        value = withContext(Dispatchers.IO) {
            book.coverPath?.let { path ->
                covers.get(path) ?: runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 768 || bounds.outHeight / inSampleSize > 1024) inSampleSize *= 2
                    }
                    BitmapFactory.decodeFile(path, options)?.also { covers.put(path, it) }
                }.getOrNull()
            }
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded.asImageBitmap(), "Обложка: ${book.title}", modifier, contentScale = ContentScale.Crop)
    } else {
        val color = Color(coverColors[(book.title.hashCode() and Int.MAX_VALUE) % coverColors.size])
        BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(color, color.copy(red = color.red * .68f, green = color.green * .68f, blue = color.blue * .68f))))) {
            val compact = maxWidth < 110.dp
            Box(Modifier.fillMaxHeight().width(7.dp).background(Color.Black.copy(alpha = .14f)))
            Column(Modifier.fillMaxSize().padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.Default.AutoStories, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(if (compact) 16.dp else 22.dp))
                Text(book.title, color = Color.White, fontFamily = FontFamily.Serif, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium, maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis)
                Text(book.authors.ifBlank { book.format }, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReadingProgress(book: BookEntity) {
    if (book.progress > 0f || book.finished) {
        LinearProgressIndicator(progress = { if (book.finished) 1f else book.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
fun BookGridCard(book: BookEntity, onOpen: () -> Unit, onDetails: () -> Unit) {
    Column(Modifier.padding(6.dp)) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Читать ${book.title}", onClick = onOpen)) {
            CoverImage(book, Modifier.fillMaxWidth().aspectRatio(.67f))
            if (book.favorite || book.finished) {
                Surface(Modifier.align(Alignment.TopEnd).padding(8.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                    Icon(if (book.finished) Icons.Default.Check else Icons.Default.Favorite, if (book.finished) "Прочитано" else "Избранное", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp).size(16.dp))
                }
            }
            Box(Modifier.align(Alignment.BottomCenter)) { ReadingProgress(book) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 10.dp).clickable(onClick = onOpen)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.authors.ifBlank { "Автор не указан" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDetails, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreHoriz, "О книге: ${book.title}") }
        }
        Text("${book.format}  ·  ${progressLabel(book)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
    }
}

@Composable
fun BookListCard(book: BookEntity, onOpen: () -> Unit, onDetails: () -> Unit, onFavorite: () -> Unit, onWant: () -> Unit, onFinished: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(book, Modifier.width(70.dp).height(104.dp).clip(RoundedCornerShape(6.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.authors.ifBlank { "Автор не указан" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${book.format} · ${progressLabel(book)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                ReadingProgress(book)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия с книгой ${book.title}") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("О книге") }, onClick = { menu = false; onDetails() })
                    DropdownMenuItem(text = { Text(if (book.favorite) "Убрать из избранного" else "В избранное") }, onClick = { menu = false; onFavorite() })
                    DropdownMenuItem(text = { Text(if (book.wantToRead) "Убрать из планов" else "Хочу прочитать") }, onClick = { menu = false; onWant() })
                    DropdownMenuItem(text = { Text(if (book.finished) "Отметить непрочитанной" else "Книга прочитана") }, onClick = { menu = false; onFinished() })
                }
            }
        }
    }
}

@Composable
fun ContinueReadingCard(book: BookEntity, onOpen: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.clickable(onClickLabel = "Продолжить чтение", onClick = onOpen).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ПРОДОЛЖИТЬ ЧТЕНИЕ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(book.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(book.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                    Text(progressLabel(book), style = MaterialTheme.typography.labelLarge)
                }
            }
            CoverImage(book, Modifier.width(86.dp).height(128.dp).clip(RoundedCornerShape(6.dp)))
        }
    }
}

private fun progressLabel(book: BookEntity): String = when {
    book.finished -> "Прочитано"
    book.lastOpenedAt == null -> "Не начата"
    else -> "${(book.progress.coerceIn(0f, 1f) * 100).toInt()}% прочитано"
}
