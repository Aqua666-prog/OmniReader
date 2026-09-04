package com.sergey.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.settings.LibraryViewMode
import com.sergey.reader.model.LibrarySection
import com.sergey.reader.ui.LibraryViewModel
import com.sergey.reader.ui.components.BookGridCard
import com.sergey.reader.ui.components.BookListCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenBook: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onResearch: () -> Unit,
    onSettings: () -> Unit
) {
    val books by vm.books.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var section by remember { mutableStateOf(LibrarySection.CURRENT) }
    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var importCopies by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<Pair<LibrarySection, String>?>(null) }

    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.importDocuments(uris, copyIntoLibrary = importCopies)
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let(vm::scanTree)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("Reader", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                LibrarySection.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = section == item && selectedGroup == null,
                        onClick = {
                            section = item
                            selectedGroup = null
                            scope.launch { drawer.close() }
                        },
                        icon = { Icon(iconFor(item), contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
                Divider(Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Цитаты и заметки") },
                    selected = false,
                    onClick = { scope.launch { drawer.close() }; onResearch() },
                    icon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Настройки") },
                    selected = false,
                    onClick = { scope.launch { drawer.close() }; onSettings() },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(selectedGroup?.second ?: section.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (selectedGroup != null) selectedGroup = null else scope.launch { drawer.open() }
                            }) {
                                Icon(if (selectedGroup != null) Icons.Default.Close else Icons.Default.Menu, contentDescription = null)
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Default.Search, contentDescription = "Поиск") }
                            IconButton(onClick = {
                                vm.setLibraryView(if (settings.libraryViewMode == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST)
                            }) {
                                Icon(if (settings.libraryViewMode == LibraryViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, contentDescription = "Вид")
                            }
                            IconButton(onClick = { openTree.launch(null) }) { Icon(Icons.Default.FolderOpen, contentDescription = "Сканировать папку") }
                            Box {
                                IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, contentDescription = "Добавить") }
                                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Открыть файлы на месте") },
                                        onClick = {
                                            addMenu = false
                                            importCopies = false
                                            openFiles.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/xml", "text/xml", "*/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Скопировать в библиотеку") },
                                        onClick = {
                                            addMenu = false
                                            importCopies = true
                                            openFiles.launch(arrayOf("application/epub+zip", "application/pdf", "text/plain", "application/xml", "text/xml", "*/*"))
                                        }
                                    )
                                }
                            }
                        }
                    )
                    if (searchVisible) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Название, автор, серия…") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Очистить") }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        ) { padding ->
            val filtered = filterBooks(books, section, selectedGroup, query)
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (section in groupSections && selectedGroup == null && query.isBlank()) {
                    GroupList(section, books) { key -> selectedGroup = section to key }
                } else if (filtered.isEmpty()) {
                    EmptyLibrary(section, onAdd = {
                        importCopies = false
                        openFiles.launch(arrayOf("*/*"))
                    })
                } else if (settings.libraryViewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { book ->
                            BookGridCard(book, onOpen = { onOpenBook(book.id) }, onDetails = { onDetails(book.id) })
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(filtered, key = { it.id }) { book ->
                            BookListCard(
                                book = book,
                                onOpen = { onOpenBook(book.id) },
                                onDetails = { onDetails(book.id) },
                                onFavorite = { vm.toggleFavorite(book) },
                                onWant = { vm.toggleWant(book) },
                                onFinished = { vm.toggleFinished(book) }
                            )
                        }
                    }
                }

                if (scanState != null) {
                    Box(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp)
                    ) {
                        androidx.compose.material3.Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.medium) {
                            Text(scanState.orEmpty(), modifier = Modifier.padding(16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private val groupSections = setOf(
    LibrarySection.AUTHORS,
    LibrarySection.SERIES,
    LibrarySection.COLLECTIONS,
    LibrarySection.FORMATS,
    LibrarySection.FOLDERS
)

private fun filterBooks(
    books: List<BookEntity>,
    section: LibrarySection,
    group: Pair<LibrarySection, String>?,
    query: String
): List<BookEntity> {
    var out = when (section) {
        LibrarySection.CURRENT -> books.filter { it.lastOpenedAt != null && !it.finished }.ifEmpty { books.filter { !it.finished }.take(20) }
        LibrarySection.ALL -> books
        LibrarySection.FAVORITES -> books.filter { it.favorite }
        LibrarySection.WANT_TO_READ -> books.filter { it.wantToRead }
        LibrarySection.FINISHED -> books.filter { it.finished }
        else -> books
    }
    group?.let { (kind, key) ->
        out = when (kind) {
            LibrarySection.AUTHORS -> out.filter { key in splitAuthors(it.authors) }
            LibrarySection.SERIES -> out.filter { it.series == key }
            LibrarySection.COLLECTIONS -> out.filter { it.collection == key }
            LibrarySection.FORMATS -> out.filter { it.format == key }
            LibrarySection.FOLDERS -> out.filter { (it.folderLabel ?: "Без папки") == key }
            else -> out
        }
    }
    if (query.isNotBlank()) {
        val q = query.trim().lowercase()
        out = out.filter {
            it.title.lowercase().contains(q) ||
                it.authors.lowercase().contains(q) ||
                (it.series?.lowercase()?.contains(q) == true) ||
                (it.collection?.lowercase()?.contains(q) == true) ||
                it.displayName.lowercase().contains(q)
        }
    }
    return out
}

@Composable
private fun GroupList(section: LibrarySection, books: List<BookEntity>, onClick: (String) -> Unit) {
    val groups: List<Pair<String, Int>> = when (section) {
        LibrarySection.AUTHORS -> books.flatMap { splitAuthors(it.authors) }.groupingBy { it }.eachCount().toList()
        LibrarySection.SERIES -> books.mapNotNull { it.series?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount().toList()
        LibrarySection.COLLECTIONS -> books.mapNotNull { it.collection?.takeIf(String::isNotBlank) }.groupingBy { it }.eachCount().toList()
        LibrarySection.FORMATS -> books.map { it.format }.groupingBy { it }.eachCount().toList()
        LibrarySection.FOLDERS -> books.map { it.folderLabel ?: "Без папки" }.groupingBy { it }.eachCount().toList()
        else -> emptyList()
    }.sortedBy { it.first.lowercase() }

    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Здесь пока пусто") }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(groups, key = { it.first }) { (name, count) ->
            ListItem(
                headlineContent = { Text(name) },
                supportingContent = { Text("Книг: $count") },
                leadingContent = { Icon(iconFor(section), contentDescription = null) },
                modifier = Modifier.clickable { onClick(name) }
            )
            Divider()
        }
    }
}

@Composable
private fun EmptyLibrary(section: LibrarySection, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Book, contentDescription = null)
        Spacer(Modifier.height(12.dp))
        Text(if (section == LibrarySection.ALL || section == LibrarySection.CURRENT) "Добавь первую книгу" else "В этом разделе пока нет книг", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) { Text("Выбрать файл") }
    }
}

private fun splitAuthors(authors: String): List<String> = authors
    .split(',', ';')
    .map { it.trim() }
    .filter { it.isNotBlank() }

private fun iconFor(section: LibrarySection) = when (section) {
    LibrarySection.CURRENT, LibrarySection.ALL -> Icons.Default.Book
    LibrarySection.FAVORITES -> Icons.Default.Favorite
    LibrarySection.WANT_TO_READ -> Icons.Default.Info
    LibrarySection.FINISHED -> Icons.Default.CheckCircle
    LibrarySection.AUTHORS -> Icons.Default.Person
    LibrarySection.SERIES, LibrarySection.COLLECTIONS -> Icons.Default.Style
    LibrarySection.FORMATS -> Icons.Default.MoreVert
    LibrarySection.FOLDERS -> Icons.Default.Folder
}
