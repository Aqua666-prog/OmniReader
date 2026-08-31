package app.omnireader.android.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnireader.android.OmniReaderApplication
import app.omnireader.android.data.db.LibraryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onManageFolders: () -> Unit, onOpen: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as OmniReaderApplication
    val items by app.container.repository.items.collectAsStateWithLifecycle(initialValue = emptyList())
    val scan by app.container.scanner.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }
    val visible = remember(items, query) {
        if (query.isBlank()) items.filter { it.isPresent } else items.filter {
            it.isPresent && listOfNotNull(it.title, it.author, it.series, it.fileName).any { value -> value.contains(query, ignoreCase = true) }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("OmniReader") },
            actions = {
                IconButton(onClick = { app.container.scanner.scanAll() }, enabled = !scan.running) { Icon(Icons.Default.Refresh, "Пересканировать") }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.Default.ViewList else Icons.Default.ViewModule, "Вид") }
                IconButton(onClick = onManageFolders) { Icon(Icons.Default.FolderOpen, "Папки") }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по библиотеке") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
            )
            if (scan.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Сканирование: ${scan.sourceName.orEmpty()} • найдено ${scan.found}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (items.isEmpty()) "Библиотека пока пуста" else "Ничего не найдено", style = MaterialTheme.typography.titleMedium)
                        if (items.isEmpty()) Text("Добавьте папку через кнопку вверху", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { BookCard(it, onOpen) }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visible, key = { it.id }) { item -> BookRow(item, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun BookCard(item: LibraryItemEntity, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(item.id) }, shape = RoundedCornerShape(14.dp)) {
        Cover(item, Modifier.fillMaxWidth().aspectRatio(0.7f))
        Column(Modifier.padding(10.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(item.series ?: item.author ?: item.format.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.progress > 0f) {
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BookRow(item: LibraryItemEntity, onOpen: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(item.id) }) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.height(92.dp).aspectRatio(0.7f))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(item.author, item.series, item.format.name).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                if (item.progress > 0f) LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Cover(item: LibraryItemEntity, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, item.coverCachePath) {
        value = withContext(Dispatchers.IO) { item.coverCachePath?.takeIf { File(it).exists() }?.let(BitmapFactory::decodeFile) }
    }
    val cover = bitmap
    if (cover != null) {
        Image(cover.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(item.format.name, style = MaterialTheme.typography.titleSmall)
        }
    }
}
