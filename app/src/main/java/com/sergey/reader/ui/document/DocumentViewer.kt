package com.sergey.reader.ui.document

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.AppContainer
import com.sergey.reader.data.db.BookEntity
import com.sergey.reader.data.settings.ReaderSettings
import com.sergey.reader.document.*
import com.sergey.reader.tts.ReaderTtsController
import com.sergey.reader.tts.ReaderTtsService
import com.sergey.reader.ui.reader.ReadingComfort
import com.sergey.reader.ui.theme.SystemBars
import com.sergey.reader.util.TextActionLauncher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map

private data class OpenDocument(val session: DocumentSession,val anchor: DocumentAnchor)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewer(container: AppContainer,book: BookEntity,initialBlock: Int?,onBack: ()->Unit) {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    val scope=rememberCoroutineScope()
    val settingsFlow=remember(container) { container.settings.settings.map { it as ReaderSettings? } }
    val loadedSettings by settingsFlow.collectAsStateWithLifecycle(initialValue=null)
    val settings=loadedSettings ?: ReaderSettings()
    val tts by ReaderTtsController.state.collectAsStateWithLifecycle()
    val marks by container.database.documentDao().marks(book.id).collectAsStateWithLifecycle(initialValue=emptyList())
    val bookmarks by container.database.bookmarkDao().observeForBook(book.id).collectAsStateWithLifecycle(initialValue=emptyList())
    var bookmarksVisible by remember { mutableStateOf(false) }
    val saveError by container.documents.error.collectAsStateWithLifecycle()
    var opened by remember(book.id) { mutableStateOf<OpenDocument?>(null) }
    var failure by remember(book.id) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var appearance by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var goto by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var current by remember(book.id) { mutableStateOf(DocumentAnchor()) }
    var selected by remember { mutableStateOf<DocumentSelection?>(null) }
    var note by remember { mutableStateOf<DocumentSelection?>(null) }
    var noteText by remember { mutableStateOf("") }
    val density=LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    var selectionHeight by remember { mutableIntStateOf(0) }
    var selectionBusy by remember { mutableStateOf(false) }
    var surface by remember(book.id) { mutableStateOf<DocumentSurfaceView?>(null) }
    val snackbar=remember { SnackbarHostState() }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun save(anchor: DocumentAnchor) {
        current=anchor
        opened?.let { container.documents.save(book.id,anchor.copy(zoom=if(settings.document.saveZoom) anchor.zoom else 1.0),it.session.sizes.size) }
    }
    val saveCallback by rememberUpdatedState<(DocumentAnchor)->Unit>({save(it)})
    ReadingComfort(settings.keepScreenOn,settings.brightness)
    DocumentFullscreen(!controls && selected==null)
    SystemBars(Color(0xFF2D2F31),darkIcons=false)
    LaunchedEffect(book.id,retry) {
        opened=null; failure=null
        var session: DocumentSession?=null
        try {
            session=DocumentSession.open(context,Uri.parse(book.uri),book.format)
            val anchor=container.documents.restore(book,initialBlock)
            opened=OpenDocument(session,anchor)
            awaitCancellation()
        } catch(cancelled: CancellationException){throw cancelled}
        catch(error: Exception){failure=DocumentErrors.message(error)}
        catch(error: LinkageError){failure=DocumentErrors.message(error)}
        catch(error: OutOfMemoryError){failure=DocumentErrors.message(error)}
        finally {session?.close()}
    }
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(book.id,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) surface?.anchor()?.let(saveCallback) }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {surface?.release(); lifecycle.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(saveError) { saveError?.let { snackbar.showSnackbar(it);container.documents.clearError() } }
    LaunchedEffect(marks,surface) {
        val decoded=withContext(Dispatchers.Default){DocumentPersistence.decodeMarks(marks)}
        surface?.setMarks(decoded)
    }
    BackHandler(selected!=null || !controls) { if(selected!=null) surface?.clearSelection() else controls=true }
    Box(Modifier.fillMaxSize().background(Color(0xFF2D2F31)).safeDrawingPadding()) {
        val ready=opened.takeIf { loadedSettings!=null }
        if(ready!=null) {
            key(ready.session) { AndroidView(factory={ctx -> DocumentSurfaceView(ctx,ready.session).also { view ->
                surface=view
                view.onPosition={saveCallback(it)}
                view.onSingleTap={controls=!controls}
                view.onSelection={selected=it}
                view.onMessage={message(it)}
                view.onBusy={selectionBusy=it}
                view.configure(settings.document)
                view.restore(ready.anchor)
            }},update={it.configure(settings.document)},modifier=Modifier.fillMaxSize().padding(
                top=with(density){(if(controls && selected==null) topHeight else 0).toDp()},
                bottom=with(density){(if(selected!=null) selectionHeight else if(controls) bottomHeight else 0).toDp()}
            )) }
        } else Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(failure==null){CircularProgressIndicator();Text("Открываем документ…",color=Color.White,modifier=Modifier.padding(16.dp))}
            else {Text(failure.orEmpty(),color=Color.White);Button(onClick={retry++}){Text("Повторить")};TextButton(onClick=onBack){Text("В библиотеку")}}
        }
        if(controls && selected==null) {
            TopAppBar(title={Text(book.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)},
                navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Назад")}},
                actions={IconButton(onClick={search=true}){Icon(Icons.Default.Search,"Поиск")};IconButton(onClick={appearance=true}){Icon(Icons.Default.Tune,"Просмотр PDF и DjVu")};IconButton(onClick={tools=true}){Icon(Icons.Default.MoreVert,"Инструменты")}},
                windowInsets=WindowInsets(0,0,0,0),modifier=Modifier.align(Alignment.TopCenter).onSizeChanged{topHeight=it.height})
            if(ready!=null) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged{bottomHeight=it.height},tonalElevation=2.dp) {
                Row(Modifier.padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={surface?.next(-1)},enabled=current.page>0){Icon(Icons.Default.ChevronLeft,"Предыдущая страница")}
                    TextButton(onClick={pageInput=(current.page+1).toString();goto=true},modifier=Modifier.weight(1f)) {
                        Text("${current.page+1} / ${ready.session.sizes.size}   ${(DocumentTransform.progress(current.page,ready.session.sizes.size)*100).toInt()}%")
                    }
                    TextButton(onClick={surface?.resetZoom()}){Text("${"%.1f".format(current.zoom)}×")}
                    IconButton(onClick={surface?.next(1)},enabled=current.page<ready.session.sizes.lastIndex){Icon(Icons.Default.ChevronRight,"Следующая страница")}
                }
            }
        }
        selected?.let { selection ->
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged{selectionHeight=it.height},tonalElevation=6.dp) {
                Column {
                    Text(selection.text,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(onClick={runCatching{clipboard.setText(AnnotatedString(selection.text))}.onSuccess{message("Текст скопирован")}.onFailure{message("Не удалось скопировать такой объём текста")}}){Text("Копировать")}
                        TextButton(onClick={surface?.selectAllOnPage()}){Text("Всё на странице")}
                        TextButton(onClick={TextActionLauncher.openUrlTemplate(context,settings.translatorUrlTemplate,selection.text).onFailure{message("Не удалось открыть переводчик")}}){Text("Перевести")}
                        TextButton(onClick={scope.launch {
                            val block=container.database.documentDao().legacyBlock(book.id,selection.page.pageIndex.toString()) ?: 0
                            container.books.addDictionaryEntry(selection.text,contextText=selection.text,bookId=book.id,blockIndex=block)
                            TextActionLauncher.openUrlTemplate(context,settings.dictionaryUrlTemplate,selection.text).onFailure{message("Не удалось открыть словарь")}
                        }}){Text("Словарь")}
                        TextButton(onClick={note=selection;noteText=""}){Text("Заметка")}
                        TextButton(onClick={scope.launch { runCatching {container.documents.annotate(book.id,selection,null,quote=true)}.onSuccess{message("Цитата сохранена")}.onFailure{message("Не удалось сохранить цитату")} }}){Text("Цитата")}
                        TextButton(onClick={surface?.clearSelection()}){Text("Закрыть")}
                    }
                }
            }
        }
        if(selectionBusy && selected==null) {
            Surface(Modifier.align(Alignment.BottomCenter).padding(bottom=88.dp),tonalElevation=6.dp,shape=MaterialTheme.shapes.large) {
                Row(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
                    Text("Распознаём текст…",modifier=Modifier.padding(start=10.dp))
                }
            }
        }
        SnackbarHost(snackbar,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=72.dp))
    }
    if(appearance) ModalBottomSheet(onDismissRequest={appearance=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("PDF и DjVu",style=MaterialTheme.typography.headlineSmall)
            DocumentSettings(settings.document){scope.launch{container.settings.setDocumentOptions(it)}}
        }
    }
    if(search) opened?.let { ready -> DocumentSearchSheet(ready.session,{search=false}) {hit -> surface?.highlight(hit)} }
    if(goto) AlertDialog(onDismissRequest={goto=false},title={Text("Перейти к странице")},text={OutlinedTextField(pageInput,{pageInput=it.filter(Char::isDigit)},singleLine=true,label={Text("Номер страницы")})},confirmButton={TextButton(onClick={pageInput.toIntOrNull()?.let {surface?.jump(it-1)};goto=false},enabled=pageInput.toIntOrNull()?.let{it in 1..(opened?.session?.sizes?.size ?: 0)}==true){Text("Перейти")}},dismissButton={TextButton(onClick={goto=false}){Text("Отмена")}})
    note?.let { selection -> AlertDialog(onDismissRequest={note=null},title={Text(if(selection.parts.last().page.pageIndex==selection.page.pageIndex) "Заметка · страница ${selection.page.pageIndex+1}" else "Заметка · страницы ${selection.page.pageIndex+1}–${selection.parts.last().page.pageIndex+1}")},text={OutlinedTextField(noteText,{noteText=it},label={Text("Ваш комментарий")})},confirmButton={TextButton(onClick={scope.launch {runCatching{container.documents.annotate(book.id,selection,noteText)}.onSuccess{note=null;message("Заметка сохранена")}.onFailure{message("Не удалось сохранить заметку")}}},enabled=noteText.isNotBlank()){Text("Сохранить")}},dismissButton={TextButton(onClick={note=null}){Text("Отмена")}}) }
    if(bookmarksVisible) ModalBottomSheet(onDismissRequest={bookmarksVisible=false}) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Закладки",style=MaterialTheme.typography.headlineSmall)
            if(bookmarks.isEmpty()) Text("Закладок пока нет")
            bookmarks.forEach { bookmark ->
                var page by remember(bookmark.id) { mutableStateOf<Int?>(null) }
                LaunchedEffect(bookmark.id) { page=container.database.documentDao().pageForLegacyParagraph(book.id,bookmark.blockIndex-1)?.toIntOrNull() }
                TextButton(onClick={page?.let{surface?.jump(it)};bookmarksVisible=false},enabled=page!=null){Text("Страница ${(page ?: 0)+1}")}
            }
        }
    }
    if(tools) ModalBottomSheet(onDismissRequest={tools=false}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Документ",style=MaterialTheme.typography.headlineSmall)
            TextButton(onClick={tools=false;scope.launch {
                val block=container.database.documentDao().legacyBlock(book.id,current.page.toString()) ?: 0
                container.books.addOrRemoveBookmark(book.id,block);message("Закладка обновлена")
            }}){Text("Закладка на этой странице")}
            TextButton(onClick={tools=false;bookmarksVisible=true}){Text("Все закладки") }
            TextButton(onClick={tools=false;surface?.resetZoom()}){Text("По ширине")}
            if(settings.ttsEnabled) {
                TextButton(onClick={tools=false;scope.launch {
                    val block=container.database.documentDao().legacyBlock(book.id,current.page.toString()) ?: 0
                    val intent=Intent(context,ReaderTtsService::class.java)
                    if(tts.active && tts.bookId==book.id) {intent.action=ReaderTtsService.ACTION_TOGGLE;context.startService(intent)}
                    else {intent.action=ReaderTtsService.ACTION_START;intent.putExtra(ReaderTtsService.EXTRA_BOOK_ID,book.id);intent.putExtra(ReaderTtsService.EXTRA_BLOCK_INDEX,block);intent.putExtra(ReaderTtsService.EXTRA_RATE,settings.ttsRate);intent.putExtra(ReaderTtsService.EXTRA_PITCH,settings.ttsPitch);ContextCompat.startForegroundService(context,intent)}
                }}){Text(if(tts.active && tts.bookId==book.id) "Пауза / продолжить озвучку" else "Читать вслух")}
                if(tts.active && tts.bookId==book.id) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0,15,30,60).forEach { minutes -> TextButton(onClick={
                        context.startService(Intent(context,ReaderTtsService::class.java).apply{action=ReaderTtsService.ACTION_SET_SLEEP_TIMER;putExtra(ReaderTtsService.EXTRA_SLEEP_MINUTES,minutes)})
                        tools=false;message(if(minutes==0) "Таймер выключен" else "Озвучка остановится через $minutes мин")
                    }) {Text(if(minutes==0) "Без таймера" else "$minutes мин")} }
                }
                if(tts.active && tts.bookId==book.id) TextButton(onClick={context.startService(Intent(context,ReaderTtsService::class.java).apply{action=ReaderTtsService.ACTION_STOP});tools=false}){Text("Остановить озвучку")}
            }
            Text("Удерживайте слово для выделения. Для сканов без текстового слоя OCR запускается автоматически; распознанный текст кэшируется локально.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
