package com.sergey.reader.ui.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sergey.reader.document.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSearchSheet(
    session: DocumentSession,
    onDismiss: () -> Unit,
    onHit: (DocumentSearchHit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<DocumentSearchHit>()) }
    var active by remember { mutableStateOf<DocumentSearchHit?>(null) }
    var scanned by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var navBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var unreadable by remember { mutableIntStateOf(0) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var navGeneration by remember { mutableIntStateOf(0) }
    var navJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        val token = ++searchGeneration
        navGeneration++
        navJob?.cancel()
        navJob = null
        navBusy = false
        hits = emptyList()
        active = null
        scanned = 0
        unreadable = 0
        status = null
        if (query.isBlank()) {
            if (token == searchGeneration) busy = false
            return@LaunchedEffect
        }
        busy = true
        try {
            delay(300)
            val needle = query.trim()
            var hasText = false
            for (index in session.sizes.indices) {
                ensureActive()
                val page = try {
                    session.text(index)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: SecurityException) {
                    throw error
                } catch (error: Exception) {
                    unreadable++
                    scanned = index + 1
                    if (unreadable >= 10) throw error
                    continue
                }
                hasText = hasText || page.text.isNotBlank()
                val remaining = (MAX_VISIBLE_HITS - hits.size).coerceAtLeast(0)
                if (remaining > 0) {
                    val found = withContext(Dispatchers.Default) { DocumentSearch.find(page, needle, remaining) }
                    if (token == searchGeneration) hits = hits + found
                }
                scanned = index + 1
                if (hits.size >= MAX_VISIBLE_HITS) {
                    status = "Показаны первые $MAX_VISIBLE_HITS совпадений. Стрелки продолжают поиск дальше списка."
                    break
                }
                yield()
            }
            if (token == searchGeneration && status == null) {
                status = when {
                    !hasText && scanned == session.sizes.size -> "Текст не найден даже после OCR"
                    hits.isEmpty() && unreadable > 0 -> "Совпадений нет; не удалось прочитать страниц: $unreadable"
                    hits.isEmpty() -> "Совпадений нет"
                    unreadable > 0 -> "Не удалось прочитать страниц: $unreadable"
                    else -> null
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LinkageError) {
            if (token == searchGeneration) status = DocumentErrors.message(error)
        } catch (error: OutOfMemoryError) {
            if (token == searchGeneration) status = DocumentErrors.message(error)
        } catch (error: Exception) {
            if (token == searchGeneration) status = DocumentErrors.message(error)
        } finally {
            if (token == searchGeneration) busy = false
        }
    }

    fun navigate(direction: Int) {
        val needle = query.trim()
        if (needle.isBlank() || navBusy) return
        val current = active
        if (current == null && direction > 0) {
            hits.firstOrNull()?.let { first -> active = first; onHit(first) }
            return
        }
        // For "previous" before a result is selected, stream from a synthetic position before
        // page 1. This finds the true last match even when the visible list is capped at 1000.
        val seed = current ?: DocumentSearchHit(0, -1, -1, "", emptyList())
        val token = ++navGeneration
        navBusy = true
        navJob?.cancel()
        navJob = scope.launch {
            var skipped = 0
            try {
                val next = DocumentSearchNavigator.adjacent(
                    count = session.sizes.size,
                    query = needle,
                    current = seed,
                    direction = direction,
                    onUnreadable = { skipped++ },
                    read = session::text,
                )
                if (token == navGeneration) {
                    if (next != null) {
                        active = next
                        onHit(next)
                        status = if (skipped > 0) "Пропущено нечитаемых страниц: $skipped" else status
                    } else {
                        status = "Других совпадений нет"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: LinkageError) {
                if (token == navGeneration) status = DocumentErrors.message(error)
            } catch (error: OutOfMemoryError) {
                if (token == navGeneration) status = DocumentErrors.message(error)
            } catch (error: Exception) {
                if (token == navGeneration) status = DocumentErrors.message(error)
            } finally {
                if (token == navGeneration) {
                    navBusy = false
                    navJob = null
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 20.dp)) {
            Text("Поиск в документе", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                query,
                { query = it },
                singleLine = true,
                label = { Text("Текст") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            if (busy) {
                LinearProgressIndicator(
                    progress = { if (session.sizes.isEmpty()) 0f else scanned.toFloat() / session.sizes.size },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Страницы: $scanned / ${session.sizes.size}", style = MaterialTheme.typography.labelSmall)
            }
            if (hits.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { navigate(-1) }, enabled = !navBusy) {
                        Icon(Icons.Default.KeyboardArrowUp, "Предыдущее совпадение")
                        Text("Предыдущее")
                    }
                    TextButton(onClick = { navigate(1) }, enabled = !navBusy) {
                        Text("Следующее")
                        Icon(Icons.Default.KeyboardArrowDown, "Следующее совпадение")
                    }
                }
            }
            if (navBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            status?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(hits) { _, hit ->
                    ListItem(
                        headlineContent = { Text("Страница ${hit.page + 1}") },
                        supportingContent = { Text(hit.snippet, maxLines = 4) },
                        modifier = Modifier.clickable {
                            active = hit
                            onHit(hit)
                        },
                    )
                }
            }
        }
    }
}

private const val MAX_VISIBLE_HITS = 1000
