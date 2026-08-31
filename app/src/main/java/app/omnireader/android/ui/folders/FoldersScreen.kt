package app.omnireader.android.ui.folders

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnireader.android.OmniReaderApplication
import app.omnireader.android.data.db.SourceFolderEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as OmniReaderApplication
    val folders = app.container.repository.folders.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val scan = app.container.scanner.state.collectAsStateWithLifecycle().value
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Папка"
        scope.launch {
            app.container.repository.addFolder(SourceFolderEntity(uri.toString(), name))
            app.container.scanner.scan(uri, name)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Папки библиотеки") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(null) }) { Icon(Icons.Default.Add, null); Text(" Добавить папку") }
                if (scan.running) Button(onClick = { app.container.scanner.cancel() }) { Icon(Icons.Default.Stop, null); Text(" Остановить") }
            }
            if (scan.running) Text("${scan.sourceName}: ${scan.currentFile.orEmpty()} • ${scan.found} найдено")
            scan.error?.let { Text("Последняя ошибка: $it") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.uri }) { folder ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(folder.displayName)
                                Text(if (folder.isAvailable) "Доступна" else "Нет доступа")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    app.container.repository.removeFolder(folder.uri)
                                    val folderUri = Uri.parse(folder.uri)
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            folderUri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                        )
                                    }.recoverCatching {
                                        context.contentResolver.releasePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                }
                            }) { Icon(Icons.Default.DeleteOutline, "Убрать источник") }
                        }
                    }
                }
            }
        }
    }
}
