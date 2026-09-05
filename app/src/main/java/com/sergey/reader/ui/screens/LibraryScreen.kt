package com.sergey.reader.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.settings.LibraryViewMode
import com.sergey.reader.data.settings.LibrarySort
import com.sergey.reader.model.LibrarySection
import com.sergey.reader.ui.LibraryViewModel
import com.sergey.reader.ui.components.*
import com.sergey.reader.util.LibraryQuery
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: LibraryViewModel, onOpenBook: (Long) -> Unit, onDetails: (Long) -> Unit, onResearch: () -> Unit, onSettings: () -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val scanState by vm.scanState.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.CURRENT.name) }
    val section = LibrarySection.valueOf(sectionName)
    var query by rememberSaveable { mutableStateOf("") }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedGroup = groupKey?.let { section to it }
    var addMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var importCopies by rememberSaveable { mutableStateOf(false) }
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.importDocuments(uris, copyIntoLibrary = importCopies)
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::scanTree) }
    fun select(item: LibrarySection) { sectionName = item.name; groupKey = null; query = "" }
    BackHandler(drawer.isOpen || groupKey != null || query.isNotEmpty()) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            query.isNotEmpty() -> query = ""
            else -> groupKey = null
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    val filtered = remember(books, section, groupKey, query, settings.librarySort) {
        LibraryQuery.select(books, section, selectedGroup, query, settings.librarySort)
    }
    val continued = remember(books) { books.filter { it.lastOpenedAt != null && !it.finished }.maxByOrNull { it.lastOpenedAt ?: 0L } }
    val home = section == LibrarySection.CURRENT && query.isBlank()
    val grouped = section in groupSections && groupKey == null && query.isBlank()
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(24.dp))
            Text("OmniReader", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text("Личное пространство для книг", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp, 8.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(LibrarySection.entries.toList()) { item ->
                    NavigationDrawerItem(label = { Text(item.label) }, selected = section == item && groupKey == null,
                        onClick = { select(item); scope.launch { drawer.close() } },
                        icon = { Icon(iconFor(item), null) }, modifier = Modifier.padding(horizontal = 12.dp))
                }
                item {
                    HorizontalDivider(Modifier.padding(24.dp, 12.dp))
                    NavigationDrawerItem(label = { Text("Цитаты и заметки") }, selected = false, onClick = onResearch, icon = { Icon(Icons.Default.FormatQuote, null) }, modifier = Modifier.padding(horizontal = 12.dp))
                    NavigationDrawerItem(label = { Text("Настройки") }, selected = false, onClick = onSettings, icon = { Icon(Icons.Default.Settings, null) }, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(title = { Text("OmniReader", style = MaterialTheme.typography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Разделы библиотеки") } },
                    actions = {
                        IconButton(onClick = { openTree.launch(null) }) { Icon(Icons.Default.FolderOpen, "Добавить папку") }
                        IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Добавить книги") }
                    })
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    NavigationBarItem(selected = section == LibrarySection.CURRENT, onClick = { select(LibrarySection.CURRENT) }, icon = { Icon(Icons.Default.AutoStories, null) }, label = { Text("Читаю") })
                    NavigationBarItem(selected = section != LibrarySection.CURRENT, onClick = { select(LibrarySection.ALL) }, icon = { Icon(Icons.Default.CollectionsBookmark, null) }, label = { Text("Книги") })
                    NavigationBarItem(selected = false, onClick = onResearch, icon = { Icon(Icons.Default.FormatQuote, null) }, label = { Text("Заметки") })
                    NavigationBarItem(selected = false, onClick = onSettings, icon = { Icon(Icons.Default.Tune, null) }, label = { Text("Настройки") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // Search is always visible; each section retains its own filtering semantics.
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Найти книгу, автора, серию") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Очистить поиск") } },
                    singleLine = true, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
                if (scanState != null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(scanState.orEmpty(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(24.dp, 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val heading: @Composable () -> Unit = {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(groupKey ?: if (home) "Время для книги" else section.label, style = MaterialTheme.typography.headlineLarge)
                                Text(if (home) "На полках: ${books.size} · Прочитано: ${books.count { it.finished }}" else "Книг: ${filtered.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.setLibraryView(if (settings.libraryViewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID) }) {
                                Icon(if (settings.libraryViewMode == LibraryViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView, "Изменить вид библиотеки")
                            }
                            Box {
                                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Сортировка") }
                                DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                                    LibrarySort.entries.forEach { order ->
                                        DropdownMenuItem(text = { Text(sortLabel(order)) }, leadingIcon = { if (settings.librarySort == order) Icon(Icons.Default.Check, null) },
                                            onClick = { sortMenu = false; vm.setLibrarySort(order) })
                                    }
                                }
                            }
                        }
                        if (home && continued != null) ContinueReadingCard(continued, onOpen = { onOpenBook(continued.id) })
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(LibrarySection.ALL, LibrarySection.FAVORITES, LibrarySection.WANT_TO_READ, LibrarySection.FINISHED).forEach { item ->
                                FilterChip(selected = section == item, onClick = { select(item) }, label = { Text(item.label) })
                            }
                        }
                    }
                }
                if (grouped) {
                    heading()
                    GroupList(section, books) { groupKey = it }
                } else if (filtered.isEmpty()) {
                    if (books.isNotEmpty()) heading()
                    if (query.isNotBlank()) {
                        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Ничего не найдено", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp))
                            Text("Попробуйте другое название или автора", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { query = "" }) { Text("Сбросить поиск") }
                        }
                    } else EmptyLibrary(section, onAdd = { addMenu = true })
                } else if (settings.libraryViewMode == LibraryViewMode.GRID) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), contentPadding = PaddingValues(bottom = 20.dp), modifier = Modifier.fillMaxSize()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "heading") { heading() }
                        items(filtered, key = { it.id }) { book ->
                            Box(Modifier.padding(horizontal = 10.dp)) { BookGridCard(book, { onOpenBook(book.id) }, { onDetails(book.id) }) }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                        item(key = "heading") { heading() }
                        items(filtered, key = { it.id }) { book ->
                            BookListCard(book, { onOpenBook(book.id) }, { onDetails(book.id) }, { vm.toggleFavorite(book) }, { vm.toggleWant(book) }, { vm.toggleFinished(book) })
                        }
                    }
                }
            }
        }
    }
    if (addMenu) {
        ModalBottomSheet(onDismissRequest = { addMenu = false }) {
            Text("Пополнить библиотеку", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp, 12.dp))
            ListItem(headlineContent = { Text("Добавить книги") }, supportingContent = { Text("Сохранить копии в приложении") }, leadingContent = { Icon(Icons.Default.Add, null) }, modifier = Modifier.clickable { addMenu = false; importCopies = true; openFiles.launch(arrayOf("*/*")) })
            ListItem(headlineContent = { Text("Открыть без копирования") }, supportingContent = { Text("Файлы останутся в исходной папке") }, leadingContent = { Icon(Icons.Default.Description, null) }, modifier = Modifier.clickable { addMenu = false; importCopies = false; openFiles.launch(arrayOf("*/*")) })
            ListItem(headlineContent = { Text("Сканировать папку") }, supportingContent = { Text("Найти книги, включая вложенные папки") }, leadingContent = { Icon(Icons.Default.FolderOpen, null) }, modifier = Modifier.clickable { addMenu = false; openTree.launch(null) })
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sortLabel(sort: LibrarySort) = when (sort) {
    LibrarySort.RECENT -> "Недавно открытые"
    LibrarySort.TITLE -> "По названию"
    LibrarySort.AUTHOR -> "По автору"
    LibrarySort.ADDED -> "Сначала новые"
    LibrarySort.PROGRESS -> "По прогрессу чтения"
}

private val groupSections = setOf(
    LibrarySection.AUTHORS,
    LibrarySection.SERIES,
    LibrarySection.COLLECTIONS,
    LibrarySection.FORMATS,
    LibrarySection.FOLDERS
)

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
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Default.AutoStories, null, modifier = Modifier.padding(28.dp).size(54.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (section == LibrarySection.ALL || section == LibrarySection.CURRENT) "Ваша библиотека начинается здесь" else "В этом разделе пока нет книг", style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Добавьте книги с устройства или выберите папку. Читайте в своём темпе — место остановки сохранится.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
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
