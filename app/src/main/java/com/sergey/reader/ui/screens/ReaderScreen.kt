package com.sergey.reader.ui.screens

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ListItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.sergey.reader.ui.reader.ReadingComfort
import com.sergey.reader.ui.theme.SystemBars
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sergey.reader.data.db.AnnotationEntity
import com.sergey.reader.data.db.AnnotationType
import com.sergey.reader.data.fonts.UserFont
import com.sergey.reader.data.settings.ContextMenuMode
import com.sergey.reader.data.settings.ReaderMode
import com.sergey.reader.data.settings.ReaderSettings
import com.sergey.reader.data.settings.ReaderThemePreset
import com.sergey.reader.model.ReaderBlock
import com.sergey.reader.tts.ReaderTtsController
import com.sergey.reader.tts.ReaderTtsService
import com.sergey.reader.tts.TtsUiState
import com.sergey.reader.ui.ReaderViewModel
import com.sergey.reader.ui.TextSelection
import com.sergey.reader.ui.reader.BookResourceRenderer
import com.sergey.reader.ui.reader.ExactPaginator
import com.sergey.reader.ui.reader.PaginationStyles
import com.sergey.reader.util.TextActionLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ReaderPalette(val background: Color, val foreground: Color, val secondary: Color)
private data class HighlightColor(val name: String, val argb: Long)

private val highlightColors = listOf(
    HighlightColor("Жёлтый", 0x66FFF59D),
    HighlightColor("Зелёный", 0x6681C784),
    HighlightColor("Голубой", 0x6679C7FF),
    HighlightColor("Розовый", 0x66F48FB1),
    HighlightColor("Оранжевый", 0x66FFB74D)
)

private fun ttsRangeFor(state: TtsUiState, currentBookId: Long?, blockIndex: Int): IntRange? {
    if (!state.active || state.bookId != currentBookId || state.blockIndex != blockIndex || !state.hasRange) return null
    return state.rangeStart until state.rangeEnd
}

private fun displayTtsRangeFor(
    state: TtsUiState,
    currentBookId: Long?,
    blocks: List<ReaderBlock>,
    blockIndex: Int
): IntRange? {
    val pageKind = blocks.getOrNull(blockIndex)?.kind
    val hiddenKind = blocks.getOrNull(blockIndex + 1)?.kind
    val target = if (
        (pageKind == ReaderBlock.Kind.PDF_PAGE && hiddenKind == ReaderBlock.Kind.PDF_TEXT) ||
        (pageKind == ReaderBlock.Kind.DJVU_PAGE && hiddenKind == ReaderBlock.Kind.DJVU_TEXT)
    ) blockIndex + 1 else blockIndex
    return ttsRangeFor(state, currentBookId, target)
}

private fun pageTextLayerFor(
    blocks: List<ReaderBlock>,
    pageBlockIndex: Int,
    annotationsByBlock: Map<Int, List<AnnotationEntity>>
): PageTextLayerPayload? {
    val pageKind = blocks.getOrNull(pageBlockIndex)?.kind ?: return null
    val expectedTextKind = when (pageKind) {
        ReaderBlock.Kind.PDF_PAGE -> ReaderBlock.Kind.PDF_TEXT
        ReaderBlock.Kind.DJVU_PAGE -> ReaderBlock.Kind.DJVU_TEXT
        else -> return null
    }
    val textIndex = pageBlockIndex + 1
    val textBlock = blocks.getOrNull(textIndex)?.takeIf { it.kind == expectedTextKind } ?: return null
    return PageTextLayerPayload(textIndex, textBlock, annotationsByBlock[textIndex].orEmpty())
}

private fun visibleBlockIndex(blocks: List<ReaderBlock>, index: Int): Int {
    val hidden = blocks.getOrNull(index)?.kind
    val visual = blocks.getOrNull(index - 1)?.kind
    if ((hidden == ReaderBlock.Kind.PDF_TEXT && visual == ReaderBlock.Kind.PDF_PAGE) ||
        (hidden == ReaderBlock.Kind.DJVU_TEXT && visual == ReaderBlock.Kind.DJVU_PAGE)) {
        return index - 1
    }
    return index.coerceIn(0, blocks.lastIndex.coerceAtLeast(0))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    initialBlock: Int? = null,
    onBack: () -> Unit
) {
    val book by vm.book.collectAsStateWithLifecycle()
    val blocks by vm.blocks.collectAsStateWithLifecycle()
    val resolvedSettings by vm.resolvedSettings.collectAsStateWithLifecycle()
    val settings = resolvedSettings
    if (settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val perBookProfile by vm.perBookProfileEnabled.collectAsStateWithLifecycle()
    val customFonts by vm.customFonts.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ttsState by ReaderTtsController.state.collectAsStateWithLifecycle()

    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    ReadingComfort(settings.keepScreenOn, settings.brightness)
    val palette = palette(settings.theme)
    SystemBars(palette.background, darkIcons = palette.background.luminance() > .5f)
    val readerFont = rememberReaderFont(settings.fontPath)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val pageWidthPx = remember(configuration.screenWidthDp, settings.horizontalPaddingDp, density) {
        with(density) {
            (configuration.screenWidthDp.toFloat() - settings.horizontalPaddingDp * 2f)
                .coerceAtLeast(180f).dp.roundToPx()
        }
    }
    val pageHeightPx = remember(configuration.screenHeightDp, density) {
        with(density) { (configuration.screenHeightDp.toFloat() - 220f).coerceAtLeast(260f).dp.roundToPx() }
    }
    val paginationStyles = remember(settings.fontSizeSp, settings.lineHeight, settings.justify, readerFont, density) {
        val paragraphSize = settings.fontSizeSp
        val footnoteSize = (settings.fontSizeSp - 2f).coerceAtLeast(13f)
        PaginationStyles(
            paragraph = TextStyle(
                fontFamily = readerFont,
                fontSize = paragraphSize.sp,
                lineHeight = (paragraphSize * settings.lineHeight).sp,
                textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
                textIndent = TextIndent(firstLine = (paragraphSize * 1.25f).sp)
            ),
            footnote = TextStyle(
                fontFamily = readerFont,
                fontSize = footnoteSize.sp,
                lineHeight = (footnoteSize * settings.lineHeight).sp,
                textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start
            ),
            chapter = TextStyle(
                fontFamily = readerFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = (settings.fontSizeSp + 4f).sp,
                lineHeight = ((settings.fontSizeSp + 4f) * settings.lineHeight).sp,
                textAlign = TextAlign.Center
            ),
            paragraphSpacingPx = with(density) { 8.dp.roundToPx() },
            footnoteSpacingPx = with(density) { 14.dp.roundToPx() },
            chapterSpacingPx = with(density) { 50.dp.roundToPx() }
        )
    }
    val pages = remember(blocks, textMeasurer, pageWidthPx, pageHeightPx, paginationStyles, settings.readerMode) {
        if (settings.readerMode == ReaderMode.VERTICAL) emptyList() else ExactPaginator.paginate(
            blocks = blocks,
            measurer = textMeasurer,
            widthPx = pageWidthPx,
            heightPx = pageHeightPx,
            styles = paginationStyles
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    var controlsVisible by remember { mutableStateOf(true) }
    var toolsVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var navigationVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var sleepTimerVisible by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var currentOffset by remember { mutableIntStateOf(0) }
    var restored by remember(book?.id, initialBlock) { mutableStateOf(false) }

    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var selectionEpoch by remember { mutableIntStateOf(0) }
    var noteSelection by remember { mutableStateOf<TextSelection?>(null) }
    var highlightSelection by remember { mutableStateOf<TextSelection?>(null) }
    var moreSelection by remember { mutableStateOf<TextSelection?>(null) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearMessage()
        }
    }

    LaunchedEffect(ttsState.error) {
        ttsState.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(settings.ttsEnabled, ttsState.active, ttsState.bookId, book?.id) {
        if (!settings.ttsEnabled && ttsState.active && ttsState.bookId == book?.id) {
            context.startService(Intent(context, ReaderTtsService::class.java).apply {
                action = ReaderTtsService.ACTION_STOP
            })
        }
    }

    LaunchedEffect(book?.id, blocks.size, initialBlock) {
        val b = book ?: return@LaunchedEffect
        if (!restored && blocks.isNotEmpty()) {
            val requested = (initialBlock ?: b.positionBlock).coerceIn(0, blocks.lastIndex)
            currentIndex = visibleBlockIndex(blocks, requested)
            currentOffset = if (initialBlock == null) b.positionOffset.coerceAtLeast(0) else 0
            vm.savePosition(requested, currentOffset)
            restored = true
        }
    }

    LaunchedEffect(settings.readerMode, restored, pages.size) {
        if (!restored || blocks.isEmpty()) return@LaunchedEffect
        if (settings.readerMode == ReaderMode.VERTICAL) {
            listState.scrollToItem(currentIndex.coerceIn(0, blocks.lastIndex))
        } else if (pages.isNotEmpty()) {
            pagerState.scrollToPage(ExactPaginator.pageForPosition(pages, currentIndex, currentOffset))
        }
    }

    LaunchedEffect(listState, blocks.size, settings.readerMode, restored) {
        if (!restored || blocks.isEmpty() || settings.readerMode != ReaderMode.VERTICAL) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(450)
            .collect { index ->
                currentIndex = index
                currentOffset = 0
                vm.savePosition(index, 0)
            }
    }

    LaunchedEffect(pagerState, pages, settings.readerMode, restored) {
        if (!restored || pages.isEmpty() || settings.readerMode != ReaderMode.PAGED) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .debounce(250)
            .collect { pageIndex ->
                val page = pages.getOrNull(pageIndex) ?: return@collect
                val first = page.slices.firstOrNull() ?: return@collect
                currentIndex = first.blockIndex
                currentOffset = first.startOffset
                vm.savePosition(currentIndex, currentOffset)
            }
    }

    LaunchedEffect(
        ttsState.active,
        ttsState.bookId,
        ttsState.blockIndex,
        ttsState.rangeStart,
        settings.readerMode,
        pages.size
    ) {
        if (!ttsState.active || ttsState.bookId != book?.id || blocks.isEmpty()) return@LaunchedEffect
        val sourceBlock = ttsState.blockIndex.coerceIn(0, blocks.lastIndex)
        val target = visibleBlockIndex(blocks, sourceBlock)
        val offset = if (ttsState.hasRange) ttsState.rangeStart else 0
        currentIndex = target
        currentOffset = offset
        if (settings.readerMode == ReaderMode.VERTICAL) {
            if (listState.firstVisibleItemIndex != target) listState.animateScrollToItem(target)
        } else if (pages.isNotEmpty()) {
            val targetPage = if ((blocks[sourceBlock].kind == ReaderBlock.Kind.PDF_TEXT || blocks[sourceBlock].kind == ReaderBlock.Kind.DJVU_TEXT)) {
                ExactPaginator.pageForBlock(pages, target)
            } else {
                ExactPaginator.pageForPosition(pages, sourceBlock, offset)
            }
            if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
        }
    }

    // Save the visible position at background/exit even when the debounce has not fired yet.
    val latestSave by rememberUpdatedState(newValue = {
        if (restored && blocks.isNotEmpty()) {
            if (settings.readerMode == ReaderMode.VERTICAL) vm.savePosition(listState.firstVisibleItemIndex, 0)
            else pages.getOrNull(pagerState.currentPage)?.slices?.firstOrNull()?.let { vm.savePosition(it.blockIndex, it.startOffset) }
        }
    })
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) latestSave() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { latestSave(); lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isBookmarked = bookmarks.any { it.blockIndex == currentIndex }
    val currentChapter = remember(currentIndex, blocks) {
        blocks.subList(0, (currentIndex + 1).coerceAtMost(blocks.size))
            .lastOrNull { it.kind == ReaderBlock.Kind.CHAPTER }
            ?.text.orEmpty()
    }
    val annotationsByBlock = remember(annotations) { annotations.groupBy { it.blockIndex } }
    val visualBlockIndices = remember(blocks) { blocks.indices.filter { blocks[it].kind != ReaderBlock.Kind.PDF_TEXT && blocks[it].kind != ReaderBlock.Kind.DJVU_TEXT } }
    val currentVisualPosition = remember(currentIndex, visualBlockIndices) {
        visualBlockIndices.indexOf(currentIndex).let { if (it >= 0) it else 0 }
    }

    fun goToInternalLink(target: String?) {
        val chapterIndex = target?.removePrefix("chapter:")?.toIntOrNull() ?: return
        val blockIndex = blocks.indexOfFirst {
            it.kind == ReaderBlock.Kind.CHAPTER && it.chapterIndex == chapterIndex
        }
        if (blockIndex < 0) return
        selection = null
        currentIndex = blockIndex
        currentOffset = 0
        if (settings.readerMode == ReaderMode.VERTICAL) {
            scope.launch { listState.scrollToItem(blockIndex) }
        } else if (pages.isNotEmpty()) {
            scope.launch { pagerState.scrollToPage(ExactPaginator.pageForBlock(pages, blockIndex)) }
        }
        vm.savePosition(blockIndex, 0)
    }

    val activeChapterIndex = blocks.getOrNull(currentIndex)?.chapterIndex ?: 0
    val chapterPageIndices = remember(pages, blocks, activeChapterIndex) {
        pages.indices.filter { pageIndex ->
            pages[pageIndex].slices.any { slice -> blocks.getOrNull(slice.blockIndex)?.chapterIndex == activeChapterIndex }
        }
    }
    val chapterPagePosition = chapterPageIndices.indexOf(pagerState.currentPage).let { if (it >= 0) it + 1 else 1 }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        if (blocks.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading) {
                    CircularProgressIndicator(color = palette.foreground)
                    Spacer(Modifier.height(20.dp))
                    Text("Открываем книгу…", color = palette.foreground)
                } else {
                    Text("Не удалось открыть книгу", style = MaterialTheme.typography.headlineSmall, color = palette.foreground)
                    Text(loadError ?: "В документе нет доступного содержимого", color = palette.foreground, modifier = Modifier.padding(vertical = 16.dp))
                    Button(onClick = vm::reload) { Text("Попробовать снова") }
                    TextButton(onClick = onBack) { Text("Вернуться в библиотеку") }
                }
            }
        } else if (settings.readerMode == ReaderMode.VERTICAL) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .pointerInput(selection) {
                        if (selection == null && settings.showControlsOnTap) detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = settings.horizontalPaddingDp.dp,
                    end = settings.horizontalPaddingDp.dp,
                    top = if (controlsVisible) 86.dp else 34.dp,
                    bottom = if (controlsVisible || selection != null) 128.dp else 52.dp
                )
            ) {
                itemsIndexed(
                    blocks,
                    key = { index, block -> "${block.kind}_${block.chapterIndex}_${block.paragraphIndex}_$index" }
                ) { index, block ->
                    ReaderBlockContent(
                        blockIndex = index,
                        block = block,
                        bookUri = book?.uri,
                        savedAnnotations = annotationsByBlock[index].orEmpty(),
                        settings = settings,
                        palette = palette,
                        fontFamily = readerFont,
                        clearSelectionSignal = selectionEpoch,
                        ttsRange = displayTtsRangeFor(ttsState, book?.id, blocks, index),
                        pageTextLayer = pageTextLayerFor(blocks, index, annotationsByBlock),
                        onInternalLink = ::goToInternalLink,
                        onSelectionChanged = { payload ->
                            selection = payload
                            if (payload != null) controlsVisible = false
                        }
                    )
                }
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val page = pages.getOrNull(pageIndex)
                if (page != null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = settings.horizontalPaddingDp.dp,
                                end = settings.horizontalPaddingDp.dp,
                                top = if (controlsVisible) 86.dp else 18.dp,
                                bottom = if (controlsVisible) 118.dp else 24.dp
                            )
                            .pointerInput(selection, pageIndex) {
                                if (selection == null && settings.showControlsOnTap) detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                            }
                    ) {
                        page.slices.forEach { slice ->
                            blocks.getOrNull(slice.blockIndex)?.let { block ->
                                val end = slice.endOffsetExclusive.coerceAtMost(block.text.length)
                                ReaderBlockContent(
                                    blockIndex = slice.blockIndex,
                                    block = block,
                                    bookUri = book?.uri,
                                    savedAnnotations = annotationsByBlock[slice.blockIndex].orEmpty(),
                                    settings = settings,
                                    palette = palette,
                                    fontFamily = readerFont,
                                    clearSelectionSignal = selectionEpoch,
                                    textStartOffset = slice.startOffset.coerceIn(0, block.text.length),
                                    textEndOffsetExclusive = end,
                                    ttsRange = displayTtsRangeFor(ttsState, book?.id, blocks, slice.blockIndex),
                                    pageTextLayer = pageTextLayerFor(blocks, slice.blockIndex, annotationsByBlock),
                                    onInternalLink = ::goToInternalLink,
                                    onSelectionChanged = { payload ->
                                        selection = payload
                                        if (payload != null) controlsVisible = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (controlsVisible || selection != null) {
            IconButton(
                onClick = { vm.toggleBookmark(currentIndex) },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = if (controlsVisible) 74.dp else 8.dp, end = 8.dp)
            ) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Закладка",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else palette.secondary
                )
            }
        }

        if (controlsVisible && selection == null) {
            Surface(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(), tonalElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(book?.title ?: "Книга", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            if (currentChapter.isNotBlank()) Text(currentChapter, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
                    },
                    actions = {
                        IconButton(onClick = { vm.toggleBookmark(currentIndex) }, enabled = blocks.isNotEmpty()) {
                            Icon(if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Закладка")
                        }
                        IconButton(onClick = { settingsVisible = true }) { Icon(Icons.Default.Settings, "Оформление чтения") }
                        IconButton(onClick = { toolsVisible = true }) { Icon(Icons.Default.MoreVert, "Инструменты чтения") }
                    }
                )
            }

            if (settings.readerMode == ReaderMode.VERTICAL) {
                ReaderBottomBar(
                    currentIndex = currentVisualPosition,
                    total = visualBlockIndices.size,
                    onSeek = { position ->
                        visualBlockIndices.getOrNull(position)?.let { target ->
                            currentIndex = target
                            currentOffset = 0
                            scope.launch { listState.scrollToItem(target) }
                            vm.savePosition(target, 0)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                PagedBottomBar(
                    currentPage = pagerState.currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                    pageCount = pages.size,
                    chapterTitle = currentChapter,
                    chapterPage = chapterPagePosition,
                    chapterPageCount = chapterPageIndices.size.coerceAtLeast(1),
                    onSeek = { page ->
                        if (pages.isEmpty()) return@PagedBottomBar
                        val safe = page.coerceIn(0, pages.lastIndex)
                        val first = pages[safe].slices.firstOrNull() ?: return@PagedBottomBar
                        currentIndex = first.blockIndex
                        currentOffset = first.startOffset
                        scope.launch { pagerState.scrollToPage(safe) }
                        vm.savePosition(currentIndex, currentOffset)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        selection?.let { selected ->
            SelectionActionBar(
                selection = selected,
                extended = settings.contextMenuMode == ContextMenuMode.EXTENDED,
                onCopy = {
                    clipboard.setText(AnnotatedString(selected.text))
                    selection = null
                    selectionEpoch++
                },
                onQuote = {
                    vm.saveQuote(selected)
                    selection = null
                    selectionEpoch++
                },
                onHighlight = { highlightSelection = selected },
                onNote = { noteSelection = selected },
                onDictionary = {
                    vm.addToDictionary(selected)
                    TextActionLauncher.openUrlTemplate(context, settings.dictionaryUrlTemplate, selected.text)
                        .onFailure { Toast.makeText(context, "Не удалось открыть словарь", Toast.LENGTH_SHORT).show() }
                    selection = null
                    selectionEpoch++
                },
                onTranslate = {
                    TextActionLauncher.openUrlTemplate(context, settings.translatorUrlTemplate, selected.text)
                        .onFailure { Toast.makeText(context, "Не удалось открыть переводчик", Toast.LENGTH_SHORT).show() }
                },
                onMore = { moreSelection = selected },
                onCancel = {
                    selection = null
                    selectionEpoch++
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (toolsVisible) {
        ModalBottomSheet(onDismissRequest = { toolsVisible = false }) {
            Text("Инструменты чтения", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp, 12.dp))
            ListItem(headlineContent = { Text("Оглавление и закладки") }, leadingContent = { Icon(Icons.Default.FormatListBulleted, null) }, modifier = Modifier.clickable { toolsVisible = false; navigationVisible = true })
            ListItem(headlineContent = { Text("Найти в книге") }, leadingContent = { Icon(Icons.Default.Search, null) }, modifier = Modifier.clickable { toolsVisible = false; searchVisible = true })
            if (settings.ttsEnabled) {
                val active = ttsState.active && ttsState.bookId == book?.id
                ListItem(headlineContent = { Text(if (active) "Пауза / продолжить озвучку" else "Читать вслух") }, leadingContent = { Icon(Icons.Default.VolumeUp, null) }, modifier = Modifier.clickable {
                    toolsVisible = false
                    book?.id?.let { id ->
                        val intent = Intent(context, ReaderTtsService::class.java)
                        if (active) {
                            intent.action = ReaderTtsService.ACTION_TOGGLE
                            context.startService(intent)
                        } else {
                            intent.action = ReaderTtsService.ACTION_START
                            intent.putExtra(ReaderTtsService.EXTRA_BOOK_ID, id)
                            intent.putExtra(ReaderTtsService.EXTRA_BLOCK_INDEX, currentIndex)
                            intent.putExtra(ReaderTtsService.EXTRA_RATE, settings.ttsRate)
                            intent.putExtra(ReaderTtsService.EXTRA_PITCH, settings.ttsPitch)
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                })
                if (active) {
                    ListItem(headlineContent = { Text("Остановить озвучку") }, leadingContent = { Icon(Icons.Default.VolumeOff, null) }, modifier = Modifier.clickable {
                        toolsVisible = false
                        context.startService(Intent(context, ReaderTtsService::class.java).apply { action = ReaderTtsService.ACTION_STOP })
                    })
                    ListItem(headlineContent = { Text("Таймер сна") }, leadingContent = { Icon(Icons.Default.Timer, null) }, modifier = Modifier.clickable { toolsVisible = false; sleepTimerVisible = true })
                }
            }
            Text("Удерживайте текст, чтобы выделить цитату, добавить заметку или открыть перевод.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(24.dp))
        }
    }

    if (settingsVisible) {
        ReaderSettingsSheet(
            settings = settings,
            perBookProfile = perBookProfile,
            customFonts = customFonts,
            vm = vm,
            onDismiss = { settingsVisible = false }
        )
    }

    if (navigationVisible) {
        NavigationDialog(
            blocks = blocks,
            currentIndex = currentIndex,
            bookmarks = bookmarks.map { it.blockIndex },
            annotations = annotations,
            onDismiss = { navigationVisible = false },
            onGo = { index ->
                navigationVisible = false
                val target = visibleBlockIndex(blocks, index)
                currentIndex = target
                currentOffset = 0
                if (settings.readerMode == ReaderMode.VERTICAL) {
                    scope.launch { listState.scrollToItem(target) }
                } else if (pages.isNotEmpty()) {
                    scope.launch { pagerState.scrollToPage(ExactPaginator.pageForBlock(pages, target)) }
                }
                vm.savePosition(index, 0)
            }
        )
    }

    if (searchVisible) {
        SearchDialog(
            results = results,
            onSearch = vm::search,
            onDismiss = { vm.clearSearch(); searchVisible = false },
            onGo = { chapter, paragraph, matchOffset ->
                val index = blocks.indexOfFirst {
                    (it.kind == ReaderBlock.Kind.PARAGRAPH || it.kind == ReaderBlock.Kind.FOOTNOTE || it.kind == ReaderBlock.Kind.PDF_TEXT || it.kind == ReaderBlock.Kind.DJVU_TEXT) &&
                        it.chapterIndex == chapter && it.paragraphIndex == paragraph
                }
                if (index >= 0) {
                    val target = visibleBlockIndex(blocks, index)
                    currentIndex = target
                    currentOffset = matchOffset.coerceAtLeast(0)
                    if (settings.readerMode == ReaderMode.VERTICAL) {
                        scope.launch { listState.scrollToItem(target) }
                    } else if (pages.isNotEmpty()) {
                        val targetPage = if ((blocks[index].kind == ReaderBlock.Kind.PDF_TEXT || blocks[index].kind == ReaderBlock.Kind.DJVU_TEXT)) {
                            ExactPaginator.pageForBlock(pages, target)
                        } else {
                            ExactPaginator.pageForPosition(pages, index, currentOffset)
                        }
                        scope.launch { pagerState.scrollToPage(targetPage) }
                    }
                    vm.savePosition(index, currentOffset)
                }
                vm.clearSearch()
                searchVisible = false
            }
        )
    }

    noteSelection?.let { selected ->
        NoteDialog(
            selectedText = selected.text,
            onDismiss = { noteSelection = null },
            onSave = { note ->
                vm.saveNote(selected, note)
                noteSelection = null
                selection = null
                selectionEpoch++
            }
        )
    }

    highlightSelection?.let { selected ->
        HighlightColorDialog(
            onDismiss = { highlightSelection = null },
            onColor = { color ->
                vm.saveHighlight(selected, color)
                highlightSelection = null
                selection = null
                selectionEpoch++
            }
        )
    }

    moreSelection?.let { selected ->
        MoreTextActionsSheet(
            onDismiss = { moreSelection = null },
            onHighlight = { highlightSelection = selected; moreSelection = null },
            onNote = { noteSelection = selected; moreSelection = null },
            onDictionary = {
                vm.addToDictionary(selected)
                TextActionLauncher.openUrlTemplate(context, settings.dictionaryUrlTemplate, selected.text)
                    .onFailure { Toast.makeText(context, "Не удалось открыть словарь", Toast.LENGTH_SHORT).show() }
                moreSelection = null
                selection = null
                selectionEpoch++
            },
            onTranslate = {
                TextActionLauncher.openUrlTemplate(context, settings.translatorUrlTemplate, selected.text)
                    .onFailure { Toast.makeText(context, "Не удалось открыть переводчик", Toast.LENGTH_SHORT).show() }
                moreSelection = null
            },
            onWebSearch = {
                TextActionLauncher.openUrlTemplate(context, settings.webSearchUrlTemplate, selected.text)
                    .onFailure { Toast.makeText(context, "Не удалось открыть поиск", Toast.LENGTH_SHORT).show() }
                moreSelection = null
            },
            onShare = {
                TextActionLauncher.shareText(context, selected.text, book?.title)
                    .onFailure { Toast.makeText(context, "Не удалось поделиться", Toast.LENGTH_SHORT).show() }
                moreSelection = null
            }
        )
    }

    if (sleepTimerVisible) {
        TtsSleepTimerDialog(
            deadlineMillis = ttsState.sleepDeadlineMillis,
            onDismiss = { sleepTimerVisible = false },
            onMinutes = { minutes ->
                val intent = Intent(context, ReaderTtsService::class.java).apply {
                    action = ReaderTtsService.ACTION_SET_SLEEP_TIMER
                    putExtra(ReaderTtsService.EXTRA_SLEEP_MINUTES, minutes)
                }
                context.startService(intent)
                sleepTimerVisible = false
            }
        )
    }

}

private data class PageTextLayerPayload(
    val blockIndex: Int,
    val block: ReaderBlock,
    val annotations: List<AnnotationEntity>
)

@Composable
private fun ReaderBlockContent(
    blockIndex: Int,
    block: ReaderBlock,
    bookUri: String?,
    savedAnnotations: List<AnnotationEntity>,
    settings: ReaderSettings,
    palette: ReaderPalette,
    fontFamily: FontFamily,
    clearSelectionSignal: Int,
    textStartOffset: Int = 0,
    textEndOffsetExclusive: Int = block.text.length,
    ttsRange: IntRange? = null,
    pageTextLayer: PageTextLayerPayload? = null,
    onInternalLink: (String?) -> Unit,
    onSelectionChanged: (TextSelection?) -> Unit
) {
    val start = textStartOffset.coerceIn(0, block.text.length)
    val end = textEndOffsetExclusive.coerceIn(start, block.text.length)
    when (block.kind) {
        ReaderBlock.Kind.CHAPTER -> Text(
            text = block.text.substring(start, end),
            color = palette.foreground,
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (settings.fontSizeSp + 4).sp,
            lineHeight = ((settings.fontSizeSp + 4) * settings.lineHeight).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 22.dp)
        )

        ReaderBlock.Kind.PARAGRAPH, ReaderBlock.Kind.FOOTNOTE -> SelectableParagraph(
            blockIndex = blockIndex,
            block = block,
            savedAnnotations = savedAnnotations,
            settings = settings,
            palette = palette,
            fontFamily = fontFamily,
            isFootnote = block.kind == ReaderBlock.Kind.FOOTNOTE,
            clearSelectionSignal = clearSelectionSignal,
            textStartOffset = start,
            textEndOffsetExclusive = end,
            ttsRange = ttsRange,
            onSelectionChanged = onSelectionChanged
        )

        ReaderBlock.Kind.LINK -> Text(
            text = block.text,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = fontFamily,
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onInternalLink(block.resourcePath) }
                .padding(vertical = 7.dp, horizontal = 4.dp)
        )
        ReaderBlock.Kind.IMAGE -> EpubImageBlock(block.resourcePath, palette)
        ReaderBlock.Kind.PDF_PAGE -> PdfPageBlock(
            bookUri = bookUri,
            pageRef = block.resourcePath,
            palette = palette,
            textLayer = pageTextLayer,
            settings = settings,
            fontFamily = fontFamily,
            clearSelectionSignal = clearSelectionSignal,
            ttsRange = pageTextLayer?.let { ttsRange },
            onSelectionChanged = onSelectionChanged
        )
        ReaderBlock.Kind.PDF_TEXT, ReaderBlock.Kind.DJVU_TEXT -> Unit // hidden layer used by search/TTS/selection sheet
        ReaderBlock.Kind.DJVU_PAGE -> DjvuPageBlock(
            bookUri = bookUri,
            pageRef = block.resourcePath,
            palette = palette,
            textLayer = pageTextLayer,
            settings = settings,
            fontFamily = fontFamily,
            clearSelectionSignal = clearSelectionSignal,
            ttsRange = pageTextLayer?.let { ttsRange },
            onSelectionChanged = onSelectionChanged
        )
    }
}

@Composable
private fun SelectableParagraph(
    blockIndex: Int,
    block: ReaderBlock,
    savedAnnotations: List<AnnotationEntity>,
    settings: ReaderSettings,
    palette: ReaderPalette,
    fontFamily: FontFamily,
    isFootnote: Boolean,
    clearSelectionSignal: Int,
    textStartOffset: Int = 0,
    textEndOffsetExclusive: Int = block.text.length,
    ttsRange: IntRange? = null,
    onSelectionChanged: (TextSelection?) -> Unit
) {
    val visibleStart = textStartOffset.coerceIn(0, block.text.length)
    val visibleEnd = textEndOffsetExclusive.coerceIn(visibleStart, block.text.length)
    val visibleText = remember(block.text, visibleStart, visibleEnd) { block.text.substring(visibleStart, visibleEnd) }
    val annotated = remember(visibleText, visibleStart, visibleEnd, savedAnnotations, ttsRange) {
        buildAnnotatedString {
            append(visibleText)
            savedAnnotations.forEach { annotation ->
                val absoluteStart = annotation.startOffset.coerceIn(0, block.text.length)
                val absoluteEnd = annotation.endOffset.coerceIn(absoluteStart, block.text.length)
                val intersectionStart = maxOf(absoluteStart, visibleStart)
                val intersectionEnd = minOf(absoluteEnd, visibleEnd)
                if (intersectionEnd > intersectionStart) {
                    addStyle(
                        SpanStyle(background = Color(annotation.colorHex.toULong())),
                        intersectionStart - visibleStart,
                        intersectionEnd - visibleStart
                    )
                }
            }
            ttsRange?.let { range ->
                val absoluteStart = maxOf(range.first, visibleStart)
                val absoluteEnd = minOf(range.last + 1, visibleEnd)
                if (absoluteEnd > absoluteStart) {
                    addStyle(
                        SpanStyle(background = Color(0x554F7DFF)),
                        absoluteStart - visibleStart,
                        absoluteEnd - visibleStart
                    )
                }
            }
        }
    }

    var value by remember(blockIndex, visibleStart, annotated) { mutableStateOf(TextFieldValue(annotatedString = annotated)) }

    LaunchedEffect(clearSelectionSignal, annotated) {
        value = TextFieldValue(annotatedString = annotated, selection = TextRange.Zero)
    }

    val effectiveSize = if (isFootnote) (settings.fontSizeSp - 2f).coerceAtLeast(13f) else settings.fontSizeSp
    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            val localStart = newValue.selection.start.coerceIn(0, visibleText.length)
            val localEnd = newValue.selection.end.coerceIn(0, visibleText.length)
            val normalizedStart = minOf(localStart, localEnd)
            val normalizedEnd = maxOf(localStart, localEnd)
            value = TextFieldValue(annotatedString = annotated, selection = TextRange(localStart, localEnd))

            if (normalizedEnd > normalizedStart) {
                onSelectionChanged(
                    TextSelection(
                        blockIndex = blockIndex,
                        startOffset = visibleStart + normalizedStart,
                        endOffset = visibleStart + normalizedEnd,
                        text = visibleText.substring(normalizedStart, normalizedEnd)
                    )
                )
            } else onSelectionChanged(null)
        },
        readOnly = true,
        textStyle = TextStyle(
            color = if (isFootnote) palette.secondary else palette.foreground,
            fontFamily = fontFamily,
            fontSize = effectiveSize.sp,
            lineHeight = (effectiveSize * settings.lineHeight).sp,
            textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            textIndent = TextIndent(firstLine = if (isFootnote || visibleStart > 0) 0.sp else (effectiveSize * 1.25f).sp)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isFootnote) 14.dp else 8.dp)
    )
}

@Composable
private fun EpubImageBlock(path: String?, palette: ReaderPalette) {
    if (!BookResourceRenderer.fileExists(path)) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullScreen by remember(path) { mutableStateOf(false) }
    var pendingSource by remember { mutableStateOf<String?>(null) }
    val ext = path?.substringAfterLast('.', "jpg")?.lowercase()?.take(8) ?: "jpg"
    val mime = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        else -> "image/jpeg"
    }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val source = pendingSource
        if (uri != null && source != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        File(source).inputStream().use { input ->
                            context.contentResolver.openOutputStream(uri, "w")?.use { output -> input.copyTo(output) }
                                ?: error("Не удалось открыть файл назначения")
                        }
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) "Иллюстрация сохранена" else "Не удалось сохранить иллюстрацию", Toast.LENGTH_SHORT).show()
            }
        }
        pendingSource = null
    }

    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = path?.let { BookResourceRenderer.loadImage(it) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Иллюстрация",
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .clickable { fullScreen = true },
                contentScale = ContentScale.Fit
            )
        } ?: Text("Иллюстрация…", color = palette.secondary)
    }

    if (fullScreen && bitmap != null) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black.copy(alpha = 0.96f)) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Иллюстрация — полный экран",
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        contentScale = ContentScale.Fit
                    )
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = {
                            pendingSource = path
                            saveImage.launch("illustration_${System.currentTimeMillis()}.$ext")
                        }) {
                            Icon(Icons.Default.Download, contentDescription = "Сохранить иллюстрацию", tint = Color.White)
                        }
                        IconButton(onClick = { fullScreen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPageBlock(
    bookUri: String?,
    pageRef: String?,
    palette: ReaderPalette,
    textLayer: PageTextLayerPayload?,
    settings: ReaderSettings,
    fontFamily: FontFamily,
    clearSelectionSignal: Int,
    ttsRange: IntRange?,
    onSelectionChanged: (TextSelection?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pageIndex = pageRef?.toIntOrNull() ?: return
    val uri = remember(bookUri) { bookUri?.let(Uri::parse) }
    if (uri == null) return
    var showTextLayer by remember(pageIndex) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val widthPx = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(480)
            val bitmap by produceState<android.graphics.Bitmap?>(null, uri, pageIndex, widthPx) {
                value = BookResourceRenderer.renderPdfPage(context, uri, pageIndex, widthPx)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Страница ${pageIndex + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text("Страница ${pageIndex + 1}…", color = palette.secondary)
            }
        }
        if (textLayer != null && textLayer.block.text.isNotBlank()) {
            TextButton(onClick = { showTextLayer = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Текст страницы")
            }
        }
    }

    if (showTextLayer && textLayer != null) {
        ModalBottomSheet(onDismissRequest = { showTextLayer = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("Страница ${pageIndex + 1} · текстовый слой", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SelectableParagraph(
                    blockIndex = textLayer.blockIndex,
                    block = textLayer.block,
                    savedAnnotations = textLayer.annotations,
                    settings = settings,
                    palette = palette,
                    fontFamily = fontFamily,
                    isFootnote = false,
                    clearSelectionSignal = clearSelectionSignal,
                    ttsRange = ttsRange,
                    onSelectionChanged = { payload ->
                        onSelectionChanged(payload)
                        if (payload != null) showTextLayer = false
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DjvuPageBlock(
    bookUri: String?,
    pageRef: String?,
    palette: ReaderPalette,
    textLayer: PageTextLayerPayload?,
    settings: ReaderSettings,
    fontFamily: FontFamily,
    clearSelectionSignal: Int,
    ttsRange: IntRange?,
    onSelectionChanged: (TextSelection?) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val pageIndex = pageRef?.toIntOrNull() ?: return
    val uri = remember(bookUri) { bookUri?.let(Uri::parse) } ?: return
    var showTextLayer by remember(pageIndex) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            val widthPx = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(480)
            val bitmap by produceState<android.graphics.Bitmap?>(null, uri, pageIndex, widthPx) {
                value = BookResourceRenderer.renderDjvuPage(context, uri, pageIndex, widthPx)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "DjVu · страница ${pageIndex + 1}",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                } ?: Text("DjVu · страница ${pageIndex + 1}…", color = palette.secondary)
            }
        }
        if (textLayer != null && textLayer.block.text.isNotBlank()) {
            TextButton(onClick = { showTextLayer = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Текст страницы")
            }
        }
    }

    if (showTextLayer && textLayer != null) {
        ModalBottomSheet(onDismissRequest = { showTextLayer = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("DjVu · страница ${pageIndex + 1} · текстовый слой", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SelectableParagraph(
                    blockIndex = textLayer.blockIndex,
                    block = textLayer.block,
                    savedAnnotations = textLayer.annotations,
                    settings = settings,
                    palette = palette,
                    fontFamily = fontFamily,
                    isFootnote = false,
                    clearSelectionSignal = clearSelectionSignal,
                    ttsRange = ttsRange,
                    onSelectionChanged = { payload ->
                        onSelectionChanged(payload)
                        if (payload != null) showTextLayer = false
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun rememberReaderFont(path: String?): FontFamily = remember(path) {
    if (path.isNullOrBlank() || !File(path).isFile) FontFamily.Serif
    else runCatching { FontFamily(Typeface.createFromFile(path)) }.getOrDefault(FontFamily.Serif)
}

@Composable
private fun TtsSleepTimerDialog(
    deadlineMillis: Long,
    onDismiss: () -> Unit,
    onMinutes: (Int) -> Unit
) {
    var now by remember(deadlineMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadlineMillis) {
        while (deadlineMillis > now) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val remaining = if (deadlineMillis > now) {
        ((deadlineMillis - now + 59_999L) / 60_000L).coerceAtLeast(1L)
    } else 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (remaining > 0) "Сейчас осталось примерно $remaining мин." else "Озвучка будет остановлена автоматически.")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        FilterChip(
                            selected = remaining == minutes.toLong(),
                            onClick = { onMinutes(minutes) },
                            label = { Text("$minutes мин") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (remaining > 0) TextButton(onClick = { onMinutes(0) }) { Text("Отключить таймер") }
            else TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = if (remaining > 0) ({ TextButton(onClick = onDismiss) { Text("Отмена") } }) else null
    )
}

@Composable
private fun SelectionActionBar(
    selection: TextSelection,
    extended: Boolean,
    onCopy: () -> Unit,
    onQuote: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onMore: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 7.dp, shadowElevation = 7.dp) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                selection.text.replace('\n', ' ').take(110),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TextAction(Icons.Default.ContentCopy, "Копировать", onCopy)
                TextAction(Icons.Default.FormatQuote, "Цитата", onQuote)
                if (extended) {
                    TextAction(Icons.Default.FormatColorFill, "Выделить", onHighlight)
                    TextAction(Icons.Default.NoteAdd, "Заметка", onNote)
                    TextAction(Icons.Default.MenuBook, "Словарь", onDictionary)
                    TextAction(Icons.Default.Translate, "Перевод", onTranslate)
                }
                TextAction(Icons.Default.MoreVert, "Ещё", onMore)
                TextButton(onClick = onCancel) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun TextAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(5.dp))
        Text(label)
    }
}

@Composable
private fun ReaderBottomBar(currentIndex: Int, total: Int, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (total <= 0) return
    var slider by remember(currentIndex, total) { mutableFloatStateOf(currentIndex.toFloat()) }
    Surface(modifier = modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 5.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val percent = if (total <= 1) 0 else (currentIndex * 100 / (total - 1))
            Text("${currentIndex + 1} из $total · $percent%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = slider.coerceIn(0f, (total - 1).toFloat()),
                onValueChange = { slider = it },
                onValueChangeFinished = { onSeek(slider.toInt().coerceIn(0, total - 1)) },
                valueRange = 0f..(total - 1).toFloat()
            )
        }
    }
}

@Composable
private fun PagedBottomBar(
    currentPage: Int,
    pageCount: Int,
    chapterTitle: String,
    chapterPage: Int,
    chapterPageCount: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 0) return
    var slider by remember(currentPage, pageCount) { mutableFloatStateOf(currentPage.toFloat()) }
    Surface(modifier = modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 5.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val percent = if (pageCount <= 1) 0 else currentPage * 100 / (pageCount - 1)
            val compactChapter = chapterTitle.ifBlank { "Глава" }.take(48)
            Text("$compactChapter · $chapterPage/$chapterPageCount", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text("Книга: ${currentPage + 1}/$pageCount · $percent%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = slider.coerceIn(0f, (pageCount - 1).toFloat()),
                onValueChange = { slider = it },
                onValueChangeFinished = { onSeek(slider.toInt().coerceIn(0, pageCount - 1)) },
                valueRange = 0f..(pageCount - 1).toFloat()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    perBookProfile: Boolean,
    customFonts: List<UserFont>,
    vm: ReaderViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Настройки чтения", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))
            val previewPalette = palette(settings.theme)
            val previewFont = rememberReaderFont(settings.fontPath)
            Surface(shape = MaterialTheme.shapes.medium, color = previewPalette.background) {
                Text("Новая глава начинается с тишины. Оставьте суету за пределами страницы и погрузитесь в историю.",
                    color = previewPalette.foreground, fontFamily = previewFont, fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeight).sp,
                    textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 20.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Яркость устройства", modifier = Modifier.weight(1f))
                Switch(checked = settings.brightness < 0f, onCheckedChange = { vm.setBrightness(if (it) -1f else .5f) })
            }
            if (settings.brightness >= 0f) {
                ReaderValueControl("Яркость", "${(settings.brightness * 100).toInt()}%", settings.brightness, .02f..1f, .05f, vm::setBrightness)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Не выключать экран при чтении", modifier = Modifier.weight(1f))
                Switch(settings.keepScreenOn, vm::setKeepScreenOn)
            }
            Text("Яркость и время работы экрана применяются ко всем книгам.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Отдельно для этой книги")
                    Text(
                        if (perBookProfile) "Изменения не затронут другие книги" else "Сейчас используются общие настройки",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = perBookProfile, onCheckedChange = vm::enablePerBookProfile)
            }

            Spacer(Modifier.height(14.dp))
            Text("Режим чтения", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.readerMode == ReaderMode.VERTICAL,
                    onClick = { vm.setReaderMode(ReaderMode.VERTICAL) },
                    label = { Text("Прокрутка") }
                )
                FilterChip(
                    selected = settings.readerMode == ReaderMode.PAGED,
                    onClick = { vm.setReaderMode(ReaderMode.PAGED) },
                    label = { Text("Страницы") }
                )
            }
            Text(
                if (settings.readerMode == ReaderMode.PAGED)
                    "Горизонтальное перелистывание. Для EPUB/FB2 страницы рассчитываются по текущей типографике."
                else "Непрерывная вертикальная прокрутка.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            Text("Шрифт", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = settings.fontPath == null,
                    onClick = { vm.setFontPath(null) },
                    label = { Text("Serif") }
                )
                customFonts.forEach { font ->
                    FilterChip(
                        selected = settings.fontPath == font.path,
                        onClick = { vm.setFontPath(font.path) },
                        label = { Text(font.name) }
                    )
                }
            }
            if (customFonts.isEmpty()) {
                Text("Свои .ttf/.otf добавляются в общих настройках.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            ReaderValueControl(
                title = "Размер текста",
                valueLabel = "${settings.fontSizeSp.toInt()} sp",
                value = settings.fontSizeSp,
                range = 14f..42f,
                step = 1f,
                onChange = vm::setFontSize
            )
            Spacer(Modifier.height(10.dp))
            ReaderValueControl(
                title = "Межстрочный интервал",
                valueLabel = "${"%.2f".format(settings.lineHeight)}×",
                value = settings.lineHeight,
                range = 1f..2f,
                step = 0.05f,
                onChange = vm::setLineHeight
            )
            Spacer(Modifier.height(10.dp))
            ReaderValueControl(
                title = "Поля страницы",
                valueLabel = "${settings.horizontalPaddingDp.toInt()} dp",
                value = settings.horizontalPaddingDp,
                range = 8f..52f,
                step = 2f,
                onChange = vm::setPadding
            )

            Spacer(Modifier.height(8.dp))
            Text("Цветовая схема", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderThemePreset.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { vm.setTheme(theme) },
                        label = { Text(themeLabel(theme)) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Выравнивать по ширине")
                Switch(checked = settings.justify, onCheckedChange = vm::setJustify)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Скрывать панели тапом")
                    Text("Тап по центру страницы включает режим без отвлекающих панелей", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.showControlsOnTap, onCheckedChange = vm::setShowControlsOnTap)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Аудиочтение")
                    Text("Выключение сразу останавливает текущую озвучку", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.ttsEnabled, onCheckedChange = vm::setTtsEnabled)
            }
        }
    }
}

@Composable
private fun ReaderValueControl(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(valueLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row {
                    TextButton(onClick = { onChange((value - step).coerceAtLeast(range.start)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    TextButton(onClick = { onChange((value + step).coerceAtMost(range.endInclusive)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
            Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationDialog(
    blocks: List<ReaderBlock>,
    currentIndex: Int,
    bookmarks: List<Int>,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val chapters = remember(blocks) { blocks.withIndex().filter { it.value.kind == ReaderBlock.Kind.CHAPTER } }
    val activeChapter = blocks.getOrNull(currentIndex)?.chapterIndex ?: 0
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.90f).padding(horizontal = 18.dp)) {
            Text("Навигация по книге", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Оглавление · ${chapters.size}") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Закладки · ${bookmarks.size}") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Цитаты") })
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                when (tab) {
                    0 -> itemsIndexed(chapters) { number, indexed ->
                        val selected = indexed.value.chapterIndex == activeChapter
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onGo(indexed.index) },
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                                    Text("${number + 1}", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.size(12.dp))
                                Text(indexed.value.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                    1 -> itemsIndexed(bookmarks) { _, blockIndex ->
                        val preview = blocks.getOrNull(blockIndex)?.text.orEmpty().take(120)
                        Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onGo(blockIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                            Text(preview.ifBlank { "Закладка" }, modifier = Modifier.padding(14.dp), maxLines = 4)
                        }
                    }
                    else -> {
                        val quotes = annotations.filter { it.type == AnnotationType.QUOTE.name || it.type == AnnotationType.HIGHLIGHT.name }
                        itemsIndexed(quotes) { _, item ->
                            Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onGo(item.blockIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                                Text(item.selectedText.take(180), modifier = Modifier.padding(14.dp), maxLines = 5)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Закрыть") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SearchDialog(
    results: List<com.sergey.reader.data.db.ParagraphEntity>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onGo: (Int, Int, Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Поиск по книге") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Текст для поиска") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSearch(query) }, enabled = query.isNotBlank()) { Text("Найти") }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(320.dp)) {
                    itemsIndexed(results) { _, result ->
                        TextButton(
                            onClick = {
                                val offset = result.text.indexOf(query, ignoreCase = true).coerceAtLeast(0)
                                onGo(result.chapterIndex, result.paragraphIndex, offset)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(result.text.take(180), modifier = Modifier.fillMaxWidth(), maxLines = 4)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun NoteDialog(selectedText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка") },
        text = {
            Column {
                Text(selectedText.take(200), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Комментарий") }, minLines = 4)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note) }, enabled = note.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun HighlightColorDialog(onDismiss: () -> Unit, onColor: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Цвет выделения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                highlightColors.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onColor(option.argb) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(28.dp).background(Color(option.argb.toULong())))
                        Spacer(Modifier.size(12.dp))
                        Text(option.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreTextActionsSheet(
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onNote: () -> Unit,
    onDictionary: () -> Unit,
    onTranslate: () -> Unit,
    onWebSearch: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Ещё", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onHighlight, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FormatColorFill, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Выделить цветом", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onNote, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.NoteAdd, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Заметка", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onDictionary, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MenuBook, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Словарь", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onTranslate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Translate, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Перевод", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onWebSearch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Language, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Веб-поиск", modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null); Spacer(Modifier.size(8.dp)); Text("Поделиться текстом", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun palette(theme: ReaderThemePreset): ReaderPalette = when (theme) {
    ReaderThemePreset.DAY -> ReaderPalette(Color(0xFFFAF7F0), Color(0xFF241F1A), Color(0xFF5F5A54))
    ReaderThemePreset.SEPIA -> ReaderPalette(Color(0xFFF2DFC1), Color(0xFF5B4731), Color(0xFF7A6750))
    ReaderThemePreset.TWILIGHT -> ReaderPalette(Color(0xFFDDD0B7), Color(0xFF40392F), Color(0xFF6D655A))
    ReaderThemePreset.NIGHT -> ReaderPalette(Color(0xFF162328), Color(0xFFD7E1E3), Color(0xFF96A6AA))
    ReaderThemePreset.AMOLED -> ReaderPalette(Color.Black, Color(0xFFE7E7E7), Color(0xFFA0A0A0))
}

private fun themeLabel(theme: ReaderThemePreset): String = when (theme) {
    ReaderThemePreset.DAY -> "День"
    ReaderThemePreset.SEPIA -> "Сепия"
    ReaderThemePreset.TWILIGHT -> "Сумерки"
    ReaderThemePreset.NIGHT -> "Ночь"
    ReaderThemePreset.AMOLED -> "AMOLED"
}
