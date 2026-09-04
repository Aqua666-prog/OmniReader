package com.sergey.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.data.db.AnnotationEntity
import com.sergey.reader.data.db.AnnotationType
import com.sergey.reader.data.db.BookmarkEntity
import com.sergey.reader.data.db.DictionaryEntryEntity
import com.sergey.reader.ui.ResearchViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(
    vm: ResearchViewModel,
    onBack: () -> Unit,
    onOpenBook: (Long, Int) -> Unit
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val dictionary by vm.dictionary.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingExport by remember { mutableStateOf("") }
    var exportMenuVisible by remember { mutableStateOf(false) }
    fun writeExport(uri: Uri?) {
        if (uri != null && pendingExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write(pendingExport)
                } ?: error("Не удалось открыть файл")
            }
        }
        pendingExport = ""
    }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { writeExport(it) }
    val exportText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { writeExport(it) }
    val exportHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { writeExport(it) }

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editEntry by remember { mutableStateOf<DictionaryEntryEntity?>(null) }
    val titles = remember(books) { books.associate { it.id to it.title } }
    val needle = query.trim().lowercase()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Цитаты, заметки и словарь") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { exportMenuVisible = true }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Экспорт")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Цитаты", "Заметки", "Закладки", "Словарь").forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                placeholder = { Text("Поиск по сохранённым материалам") }
            )

            when (tab) {
                0 -> {
                    val items = annotations.filter {
                        it.type == AnnotationType.QUOTE.name || it.type == AnnotationType.HIGHLIGHT.name
                    }.filter {
                        needle.isBlank() || it.selectedText.lowercase().contains(needle) || titles[it.bookId].orEmpty().lowercase().contains(needle)
                    }
                    AnnotationList(
                        items = items,
                        titles = titles,
                        onOpenBook = onOpenBook,
                        onDelete = vm::deleteAnnotation
                    )
                }
                1 -> {
                    val items = annotations.filter { it.type == AnnotationType.NOTE.name }.filter {
                        needle.isBlank() || it.selectedText.lowercase().contains(needle) || it.note.orEmpty().lowercase().contains(needle) || titles[it.bookId].orEmpty().lowercase().contains(needle)
                    }
                    AnnotationList(
                        items = items,
                        titles = titles,
                        onOpenBook = onOpenBook,
                        onDelete = vm::deleteAnnotation
                    )
                }
                2 -> {
                    val items = bookmarks.filter {
                        needle.isBlank() || titles[it.bookId].orEmpty().lowercase().contains(needle) || it.label.orEmpty().lowercase().contains(needle) || it.note.orEmpty().lowercase().contains(needle)
                    }
                    if (items.isEmpty()) {
                        EmptyResearch("Глобальных закладок пока нет.")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(items, key = { it.id }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.label ?: titles[item.bookId] ?: "Закладка") },
                                    supportingContent = {
                                        Text(
                                            buildString {
                                                append(titles[item.bookId] ?: "Книга")
                                                append(" · позиция ")
                                                append(item.blockIndex + 1)
                                                item.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                            }
                                        )
                                    },
                                    modifier = Modifier.clickable { onOpenBook(item.bookId, item.blockIndex) },
                                    trailingContent = {
                                        IconButton(onClick = { vm.deleteBookmark(item.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
                else -> {
                    val items = dictionary.filter {
                        needle.isBlank() || it.term.lowercase().contains(needle) || it.definition.orEmpty().lowercase().contains(needle) || it.translation.orEmpty().lowercase().contains(needle)
                    }
                    if (items.isEmpty()) {
                        EmptyResearch("Словарь пока пуст. Выдели слово в книге и нажми «Словарь».")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(items, key = { it.id }) { entry ->
                                ListItem(
                                    headlineContent = { Text(entry.term) },
                                    supportingContent = {
                                        Column {
                                            entry.translation?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                            entry.definition?.takeIf { it.isNotBlank() }?.let { Text(it) }
                                            entry.contextText?.takeIf { it.isNotBlank() }?.let {
                                                Spacer(Modifier.height(4.dp))
                                                Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic)
                                            }
                                            Text(
                                                entry.bookId?.let { titles[it] }.orEmpty(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        entry.bookId?.let { bookId -> onOpenBook(bookId, entry.blockIndex ?: 0) }
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(onClick = { editEntry = entry }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                                            }
                                            IconButton(onClick = { vm.deleteDictionary(entry.id) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (exportMenuVisible) {
        AlertDialog(
            onDismissRequest = { exportMenuVisible = false },
            title = { Text("Экспорт") },
            text = { Text("Выбери формат для текущей вкладки.") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingExport = buildResearchMarkdown(tab, annotations, bookmarks, dictionary, titles)
                        exportMenuVisible = false
                        exportMarkdown.launch(exportFileName(tab, "md"))
                    }) { Text("Markdown") }
                    TextButton(onClick = {
                        pendingExport = buildResearchText(tab, annotations, bookmarks, dictionary, titles)
                        exportMenuVisible = false
                        exportText.launch(exportFileName(tab, "txt"))
                    }) { Text("TXT") }
                    TextButton(onClick = {
                        pendingExport = buildResearchHtml(tab, annotations, bookmarks, dictionary, titles)
                        exportMenuVisible = false
                        exportHtml.launch(exportFileName(tab, "html"))
                    }) { Text("HTML") }
                }
            },
            dismissButton = { TextButton(onClick = { exportMenuVisible = false }) { Text("Отмена") } }
        )
    }

    editEntry?.let { entry ->
        DictionaryEditDialog(
            entry = entry,
            onDismiss = { editEntry = null },
            onSave = {
                vm.updateDictionary(it)
                editEntry = null
            }
        )
    }
}

@Composable
private fun AnnotationList(
    items: List<AnnotationEntity>,
    titles: Map<Long, String>,
    onOpenBook: (Long, Int) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyResearch("Здесь пока ничего нет. Выдели текст в книге и сохрани цитату или заметку.")
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            ListItem(
                headlineContent = {
                    Text(
                        item.selectedText,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    Column {
                        item.note?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontStyle = FontStyle.Italic)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append(titles[item.bookId] ?: "Книга")
                                append(" · ")
                                append(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.createdAt)))
                                if (item.type == AnnotationType.HIGHLIGHT.name) append(" · выделение")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenBook(item.bookId, item.blockIndex) },
                trailingContent = {
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyResearch(text: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun buildResearchMarkdown(
    tab: Int,
    annotations: List<AnnotationEntity>,
    bookmarks: List<BookmarkEntity>,
    dictionary: List<DictionaryEntryEntity>,
    titles: Map<Long, String>
): String = buildString {
    when (tab) {
        0 -> {
            appendLine("# Цитаты и выделения")
            appendLine()
            annotations
                .filter { it.type == AnnotationType.QUOTE.name || it.type == AnnotationType.HIGHLIGHT.name }
                .forEach { item ->
                    appendLine("## ${titles[item.bookId] ?: "Книга"}")
                    appendLine()
                    item.selectedText.lines().forEach { appendLine("> $it") }
                    item.note?.takeIf { it.isNotBlank() }?.let { appendLine("\n**Комментарий:** $it") }
                    appendLine()
                }
        }
        1 -> {
            appendLine("# Заметки")
            appendLine()
            annotations.filter { it.type == AnnotationType.NOTE.name }.forEach { item ->
                appendLine("## ${titles[item.bookId] ?: "Книга"}")
                appendLine()
                item.selectedText.lines().forEach { appendLine("> $it") }
                appendLine()
                appendLine(item.note.orEmpty())
                appendLine()
            }
        }
        2 -> {
            appendLine("# Закладки")
            appendLine()
            bookmarks.forEach { item ->
                appendLine("- **${titles[item.bookId] ?: "Книга"}** — позиция ${item.blockIndex + 1}")
                item.label?.takeIf { it.isNotBlank() }?.let { appendLine("  - $it") }
                item.note?.takeIf { it.isNotBlank() }?.let { appendLine("  - $it") }
            }
        }
        else -> {
            appendLine("# Словарь")
            appendLine()
            dictionary.forEach { entry ->
                append("- **${entry.term}**")
                entry.translation?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                appendLine()
                entry.definition?.takeIf { it.isNotBlank() }?.let { appendLine("  - $it") }
                entry.contextText?.takeIf { it.isNotBlank() }?.let { appendLine("  - Контекст: $it") }
            }
        }
    }
}

private fun exportFileName(tab: Int, extension: String): String {
    val stem = when (tab) {
        0 -> "reader-quotes"
        1 -> "reader-notes"
        2 -> "reader-bookmarks"
        else -> "reader-dictionary"
    }
    return "$stem.$extension"
}

private fun buildResearchText(
    tab: Int,
    annotations: List<AnnotationEntity>,
    bookmarks: List<BookmarkEntity>,
    dictionary: List<DictionaryEntryEntity>,
    titles: Map<Long, String>
): String = buildString {
    when (tab) {
        0 -> {
            appendLine("ЦИТАТЫ И ВЫДЕЛЕНИЯ")
            appendLine()
            annotations.filter { it.type == AnnotationType.QUOTE.name || it.type == AnnotationType.HIGHLIGHT.name }.forEach { item ->
                appendLine(titles[item.bookId] ?: "Книга")
                appendLine(item.selectedText)
                item.note?.takeIf { it.isNotBlank() }?.let { appendLine("Комментарий: $it") }
                appendLine()
            }
        }
        1 -> {
            appendLine("ЗАМЕТКИ")
            appendLine()
            annotations.filter { it.type == AnnotationType.NOTE.name }.forEach { item ->
                appendLine(titles[item.bookId] ?: "Книга")
                appendLine(item.selectedText)
                item.note?.takeIf { it.isNotBlank() }?.let { appendLine("Заметка: $it") }
                appendLine()
            }
        }
        2 -> {
            appendLine("ЗАКЛАДКИ")
            appendLine()
            bookmarks.forEach { item ->
                appendLine("${titles[item.bookId] ?: "Книга"} — позиция ${item.blockIndex + 1}")
                item.label?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                item.note?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                appendLine()
            }
        }
        else -> {
            appendLine("СЛОВАРЬ")
            appendLine()
            dictionary.forEach { entry ->
                append(entry.term)
                entry.translation?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                appendLine()
                entry.definition?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
                entry.contextText?.takeIf { it.isNotBlank() }?.let { appendLine("Контекст: $it") }
                appendLine()
            }
        }
    }
}

private fun buildResearchHtml(
    tab: Int,
    annotations: List<AnnotationEntity>,
    bookmarks: List<BookmarkEntity>,
    dictionary: List<DictionaryEntryEntity>,
    titles: Map<Long, String>
): String = buildString {
    appendLine("<!doctype html>")
    appendLine("<html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    appendLine("<title>Reader export</title><style>body{font-family:serif;max-width:860px;margin:40px auto;padding:0 20px;line-height:1.55}blockquote{border-left:3px solid #888;padding-left:14px;margin-left:0}.meta{opacity:.7;font-size:.9em}article{margin:0 0 2em}</style></head><body>")
    val heading = when (tab) { 0 -> "Цитаты и выделения"; 1 -> "Заметки"; 2 -> "Закладки"; else -> "Словарь" }
    appendLine("<h1>${escapeHtml(heading)}</h1>")
    when (tab) {
        0, 1 -> {
            val wanted = if (tab == 0) setOf(AnnotationType.QUOTE.name, AnnotationType.HIGHLIGHT.name) else setOf(AnnotationType.NOTE.name)
            annotations.filter { it.type in wanted }.forEach { item ->
                appendLine("<article><h2>${escapeHtml(titles[item.bookId] ?: "Книга")}</h2>")
                appendLine("<blockquote>${escapeHtml(item.selectedText).replace("\n", "<br>")}</blockquote>")
                item.note?.takeIf { it.isNotBlank() }?.let { appendLine("<p><strong>Комментарий:</strong> ${escapeHtml(it)}</p>") }
                appendLine("</article>")
            }
        }
        2 -> bookmarks.forEach { item ->
            appendLine("<article><h2>${escapeHtml(titles[item.bookId] ?: "Книга")}</h2><p>Позиция ${item.blockIndex + 1}</p>")
            item.label?.takeIf { it.isNotBlank() }?.let { appendLine("<p>${escapeHtml(it)}</p>") }
            item.note?.takeIf { it.isNotBlank() }?.let { appendLine("<p>${escapeHtml(it)}</p>") }
            appendLine("</article>")
        }
        else -> dictionary.forEach { entry ->
            appendLine("<article><h2>${escapeHtml(entry.term)}</h2>")
            entry.translation?.takeIf { it.isNotBlank() }?.let { appendLine("<p><strong>Перевод:</strong> ${escapeHtml(it)}</p>") }
            entry.definition?.takeIf { it.isNotBlank() }?.let { appendLine("<p>${escapeHtml(it)}</p>") }
            entry.contextText?.takeIf { it.isNotBlank() }?.let { appendLine("<blockquote>${escapeHtml(it)}</blockquote>") }
            appendLine("</article>")
        }
    }
    appendLine("</body></html>")
}

private fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

@Composable
private fun DictionaryEditDialog(
    entry: DictionaryEntryEntity,
    onDismiss: () -> Unit,
    onSave: (DictionaryEntryEntity) -> Unit
) {
    var term by remember(entry.id) { mutableStateOf(entry.term) }
    var translation by remember(entry.id) { mutableStateOf(entry.translation.orEmpty()) }
    var definition by remember(entry.id) { mutableStateOf(entry.definition.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Словарная карточка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(term, { term = it }, label = { Text("Термин") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(translation, { translation = it }, label = { Text("Перевод") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(definition, { definition = it }, label = { Text("Определение") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        },
        confirmButton = {
            TextButton(
                enabled = term.isNotBlank(),
                onClick = {
                    onSave(
                        entry.copy(
                            term = term.trim(),
                            translation = translation.trim().takeIf { it.isNotBlank() },
                            definition = definition.trim().takeIf { it.isNotBlank() }
                        )
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
