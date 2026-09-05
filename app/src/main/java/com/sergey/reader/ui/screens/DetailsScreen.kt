package com.sergey.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.ui.DetailsViewModel
import com.sergey.reader.ui.components.CoverImage
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(vm: DetailsViewModel, onBack: () -> Unit, onRead: (Long) -> Unit) {
    val book by vm.book.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("О книге") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    IconButton(onClick = { edit = true }, enabled = book != null) { Icon(Icons.Default.Edit, contentDescription = "Редактировать") }
                }
            )
        }
    ) { padding ->
        val b = book
        if (b == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { Text("Загрузка…") }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverImage(b, Modifier.size(width = 158.dp, height = 236.dp).clip(MaterialTheme.shapes.small))
                    Spacer(Modifier.height(24.dp))
                    Text(b.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    if (b.authors.isNotBlank()) Text(b.authors, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { onRead(b.id) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text(if (b.lastOpenedAt == null) "Начать чтение" else "Продолжить чтение")
                    }
                }

                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(progress = { b.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("Прочитано ${(b.progress * 100).toInt()}%", modifier = Modifier.padding(top = 6.dp))

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = vm::toggleFavorite) { Icon(if (b.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Избранное") }
                    IconButton(onClick = vm::toggleWant) { Icon(Icons.Default.Schedule, contentDescription = "Хочу прочитать", tint = if (b.wantToRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = vm::toggleFinished) { Icon(Icons.Default.CheckCircle, contentDescription = "Прочитано", tint = if (b.finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Удалить из библиотеки") }
                }

                Meta("Серия", buildString {
                    append(b.series ?: "—")
                    b.seriesIndex?.let { append(" · том ${formatIndex(it)}") }
                })
                Meta("Коллекция", b.collection ?: "—")
                Meta("Формат", "${b.format}, ${formatSize(b.sizeBytes)}")
                Meta("Язык", b.language ?: "—")
                Meta("Слов", if (b.wordCount > 0) "%,d".format(b.wordCount) else "—")
                Meta("Папка", b.folderLabel ?: "—")
                Meta("Добавлено", DateFormat.getDateTimeInstance().format(Date(b.addedAt)))
                b.lastOpenedAt?.let { Meta("Последнее чтение", DateFormat.getDateTimeInstance().format(Date(it))) }
                if (!b.annotation.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text("Аннотация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(b.annotation, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }

    if (edit) book?.let { b ->
        EditBookDialog(b, onDismiss = { edit = false }, onSave = { vm.save(it); edit = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить из библиотеки?") },
            text = { Text("Запись и извлечённый текст будут удалены. Исходный файл останется на устройстве.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Spacer(Modifier.height(16.dp))
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EditBookDialog(book: BookEntity, onDismiss: () -> Unit, onSave: (BookEntity) -> Unit) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var authors by remember(book.id) { mutableStateOf(book.authors) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesIndex by remember(book.id) { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var collection by remember(book.id) { mutableStateOf(book.collection.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Метаданные") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(authors, { authors = it }, label = { Text("Авторы") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(series, { series = it }, label = { Text("Серия") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(seriesIndex, { seriesIndex = it }, label = { Text("Номер тома") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(collection, { collection = it }, label = { Text("Коллекция") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(book.copy(
                    title = title.trim().ifBlank { book.title },
                    authors = authors.trim(),
                    series = series.trim().ifBlank { null },
                    seriesIndex = seriesIndex.replace(',', '.').toDoubleOrNull(),
                    collection = collection.trim().ifBlank { null }
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "размер неизвестен"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) "${DecimalFormat("0.#").format(mb)} МБ" else "${bytes / 1024} КБ"
}

private fun formatIndex(index: Double): String = if (index % 1.0 == 0.0) index.toInt().toString() else index.toString()
