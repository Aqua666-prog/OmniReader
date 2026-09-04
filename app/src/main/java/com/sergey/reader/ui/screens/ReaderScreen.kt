package com.sergey.reader.ui.screens

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
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
    val target = if (
        blocks.getOrNull(blockIndex)?.kind == ReaderBlock.Kind.PDF_PAGE &&
        blocks.getOrNull(blockIndex + 1)?.kind == ReaderBlock.Kind.PDF_TEXT
    ) blockIndex + 1 else blockIndex
    return ttsRangeFor(state, currentBookId, target)
}

private fun pdfTextLayerFor(
    blocks: List<ReaderBlock>,
    pageBlockIndex: Int,
    annotationsByBlock: Map<Int, List<AnnotationEntity>>
): PdfTextLayerPayload? {
    if (blocks.getOrNull(pageBlockIndex)?.kind != ReaderBlock.Kind.PDF_PAGE) return null
    val textIndex = pageBlockIndex + 1
    val textBlock = blocks.getOrNull(textIndex)?.takeIf { it.kind == ReaderBlock.Kind.PDF_TEXT } ?: return null
    return PdfTextLayerPayload(textIndex, textBlock, annotationsByBlock[textIndex].orEmpty())
}

private fun visibleBlockIndex(blocks: List<ReaderBlock>, index: Int): Int {
    if (blocks.getOrNull(index)?.kind == ReaderBlock.Kind.PDF_TEXT && blocks.getOrNull(index - 1)?.kind == ReaderBlock.Kind.PDF_PAGE) {
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    val perBookProfile by vm.perBookProfileEnabled.collectAsStateWithLifecycle()
    val customFonts by vm.customFonts.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val annotations by vm.annotations.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ttsState by ReaderTtsController.state.collectAsStateWithLifecycle()

    val palette = palette(settings.theme)
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
    val pages = remember(blocks, textMeasurer, pageWidthPx, pageHeightPx, paginationStyles) {
        ExactPaginator.paginate(
            blocks = blocks,
            measurer = textMeasurer,
            widthPx = pageWidthPx,
            heightPx = pageHeightPx,
            styles = paginationStyles
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    var controlsVisible by remember { mutableStateOf(true) }
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

    LaunchedEffect(book?.id, blocks.size, initialBlock) {
        val b = book ?: return@LaunchedEffect
        if (!restored && blocks.isNotEmpty()) {
            val requested = (initialBlock ?: b.positionBlock).coerceIn(0, blocks.lastIndex)
            currentIndex = visibleBlockIndex(blocks, requested)
            currentOffset = b.positionOffset.coerceAtLeast(0)
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

    LaunchedEffect(listState, blocks.size, settings.readerMode) {
        if (blocks.isEmpty() || settings.readerMode != ReaderMode.VERTICAL) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(450)
            .collect { index ->
                currentIndex = index
                currentOffset = 0
                vm.savePosition(index, 0)
            }
    }

    LaunchedEffect(pagerState, pages, settings.readerMode) {
        if (pages.isEmpty() || settings.readerMode != ReaderMode.PAGED) return@LaunchedEffect
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
            val targetPage = if (blocks[sourceBlock].kind == ReaderBlock.Kind.PDF_TEXT) {
                ExactPaginator.pageForBlock(pages, target)
            } else {
                ExactPaginator.pageForPosition(pages, sourceBlock, offset)
            }
            if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
        }
    }

    val isBookmarked = bookmarks.any { it.blockIndex == currentIndex }
    val currentChapter = remember(currentIndex, blocks) {
        blocks.subList(0, (currentIndex + 1).coerceAtMost(blocks.size))
            .lastOrNull { it.kind == ReaderBlock.Kind.CHAPTER }
            ?.text.orEmpty()
    }
    val annotationsByBlock = remember(annotations) { annotations.groupBy { it.blockIndex } }
    val visualBlockIndices = remember(blocks) { blocks.indices.filter { blocks[it].kind != ReaderBlock.Kind.PDF_TEXT } }
    val currentVisualPosition = remember(currentIndex, visualBlockIndices) {
        visualBlockIndices.indexOf(currentIndex).let { if (it >= 0) it else 0 }
    }

    Box(Modifier.fillMaxSize().background(palette.background)) {
        if (blocks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Подготовка книги…", color = palette.foreground)
            }
        } else if (settings.readerMode == ReaderMode.VERTICAL) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .pointerInput(selection) {
                        if (selection == null) detectTapGestures(onTap = { controlsVisible = !controlsVisible })
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
                        pdfTextLayer = pdfTextLayerFor(blocks, index, annotationsByBlock),
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
                                top = 86.dp,
                                bottom = 118.dp
                            )
                            .pointerInput(selection, pageIndex) {
                                if (selection == null) detectTapGestures(onTap = { controlsVisible = !controlsVisible })
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
                                    pdfTextLayer = pdfTextLayerFor(blocks, slice.blockIndex, annotationsByBlock),
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
                        IconButton(onClick = {
                            val currentBookId = book?.id ?: return@IconButton
                            val intent = Intent(context, ReaderTtsService::class.java)
                            if (ttsState.active && ttsState.bookId == currentBookId) {
                                intent.action = ReaderTtsService.ACTION_TOGGLE
                                context.startService(intent)
                            } else {
                                intent.action = ReaderTtsService.ACTION_START
                                intent.putExtra(ReaderTtsService.EXTRA_BOOK_ID, currentBookId)
                                intent.putExtra(ReaderTtsService.EXTRA_BLOCK_INDEX, currentIndex)
                                intent.putExtra(ReaderTtsService.EXTRA_RATE, settings.ttsRate)
                                intent.putExtra(ReaderTtsService.EXTRA_PITCH, settings.ttsPitch)
                                ContextCompat.startForegroundService(context, intent)
                            }
                        }) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = if (ttsState.active && ttsState.bookId == book?.id) "Пауза / продолжить озвучку" else "Озвучка",
                                tint = if (ttsState.active && ttsState.bookId == book?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (ttsState.active && ttsState.bookId == book?.id) {
                            IconButton(onClick = { sleepTimerVisible = true }) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = "Таймер сна",
                                    tint = if (ttsState.sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { searchVisible = true },
                            enabled = book?.format != "PDF" || blocks.any { it.kind == ReaderBlock.Kind.PDF_TEXT }
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = { navigationVisible = true }) {
                            Icon(Icons.Default.FormatListBulleted, contentDescription = "Оглавление")
                        }
                        IconButton(onClick = { settingsVisible = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                        IconButton(onClick = {
                            Toast.makeText(context, "Выдели текст для цитаты, заметки, словаря или перевода", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                        }
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
                    onSeek = { page ->
                        val safe = page.coerceIn(0, pages.lastIndex)
                        val first = pages[safe].slices.first()
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
                    (it.kind == ReaderBlock.Kind.PARAGRAPH || it.kind == ReaderBlock.Kind.FOOTNOTE || it.kind == ReaderBlock.Kind.PDF_TEXT) &&
                        it.chapterIndex == chapter && it.paragraphIndex == paragraph
                }
                if (index >= 0) {
                    val target = visibleBlockIndex(blocks, index)
                    currentIndex = target
                    currentOffset = matchOffset.coerceAtLeast(0)
                    if (settings.readerMode == ReaderMode.VERTICAL) {
                        scope.launch { listState.scrollToItem(target) }
                    } else if (pages.isNotEmpty()) {
                        val targetPage = if (blocks[index].kind == ReaderBlock.Kind.PDF_TEXT) {
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

private data class PdfTextLayerPayload(
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
    pdfTextLayer: PdfTextLayerPayload? = null,
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

        ReaderBlock.Kind.IMAGE -> EpubImageBlock(block.resourcePath, palette)
        ReaderBlock.Kind.PDF_PAGE -> PdfPageBlock(
            bookUri = bookUri,
            pageRef = block.resourcePath,
            palette = palette,
            textLayer = pdfTextLayer,
            settings = settings,
            fontFamily = fontFamily,
            clearSelectionSignal = clearSelectionSignal,
            ttsRange = pdfTextLayer?.let { ttsRange },
            onSelectionChanged = onSelectionChanged
        )
        ReaderBlock.Kind.PDF_TEXT -> Unit // hidden layer used by PDF search/TTS/selection sheet
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
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = path) {
        value = path?.let { BookResourceRenderer.loadImage(it) }
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Иллюстрация",
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                contentScale = ContentScale.Fit
            )
        } ?: Text("Иллюстрация…", color = palette.secondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfPageBlock(
    bookUri: String?,
    pageRef: String?,
    palette: ReaderPalette,
    textLayer: PdfTextLayerPayload?,
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
private fun PagedBottomBar(currentPage: Int, pageCount: Int, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pageCount <= 0) return
    var slider by remember(currentPage, pageCount) { mutableFloatStateOf(currentPage.toFloat()) }
    Surface(modifier = modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 5.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val percent = if (pageCount <= 1) 0 else currentPage * 100 / (pageCount - 1)
            Text("Страница ${currentPage + 1} из $pageCount · $percent%", style = MaterialTheme.typography.titleMedium)
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
            Text("Размер шрифта: ${settings.fontSizeSp.toInt()}")
            Slider(value = settings.fontSizeSp, onValueChange = vm::setFontSize, valueRange = 14f..42f)

            Text("Межстрочный интервал: ${"%.2f".format(settings.lineHeight)}")
            Slider(value = settings.lineHeight, onValueChange = vm::setLineHeight, valueRange = 1f..2f)

            Text("Поля: ${settings.horizontalPaddingDp.toInt()} dp")
            Slider(value = settings.horizontalPaddingDp, onValueChange = vm::setPadding, valueRange = 8f..52f)

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
        }
    }
}

@Composable
private fun NavigationDialog(
    blocks: List<ReaderBlock>,
    bookmarks: List<Int>,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (tab) { 0 -> "Оглавление"; 1 -> "Закладки"; else -> "Цитаты" }) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Оглавление") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Закладки") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Цитаты") })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.height(420.dp)) {
                    when (tab) {
                        0 -> {
                            val chapters = blocks.withIndex().filter { it.value.kind == ReaderBlock.Kind.CHAPTER }
                            itemsIndexed(chapters) { _, indexed ->
                                TextButton(onClick = { onGo(indexed.index) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(indexed.value.text, modifier = Modifier.fillMaxWidth())
                                }
                                HorizontalDivider()
                            }
                        }
                        1 -> itemsIndexed(bookmarks) { _, blockIndex ->
                            val preview = blocks.getOrNull(blockIndex)?.text.orEmpty().take(90)
                            TextButton(onClick = { onGo(blockIndex) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${blockIndex + 1}. $preview", modifier = Modifier.fillMaxWidth())
                            }
                            HorizontalDivider()
                        }
                        else -> {
                            val quotes = annotations.filter { it.type == AnnotationType.QUOTE.name || it.type == AnnotationType.HIGHLIGHT.name }
                            itemsIndexed(quotes) { _, item ->
                                TextButton(onClick = { onGo(item.blockIndex) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(item.selectedText.take(150), modifier = Modifier.fillMaxWidth(), maxLines = 4)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
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
