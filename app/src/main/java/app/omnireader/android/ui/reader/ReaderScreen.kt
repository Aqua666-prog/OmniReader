package app.omnireader.android.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.omnireader.android.OmniReaderApplication
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.reader.ComicReaderSession
import app.omnireader.android.reader.PagedBitmapReaderSession
import app.omnireader.android.reader.ReaderSession
import app.omnireader.android.reader.TextReaderSession
import com.t8rin.tiff_coder.TiffCoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.io.File


private sealed interface BitmapLoadState {
    data object Loading : BitmapLoadState
    data class Ready(val bitmap: Bitmap) : BitmapLoadState
    data class Error(val message: String) : BitmapLoadState
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val session: ReaderSession) : LoadState
    data class Error(val message: String) : LoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(itemId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as OmniReaderApplication
    val item by app.container.repository.observeItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val load by produceState<LoadState>(initialValue = LoadState.Loading, item?.uri, item?.format) {
        val current = item ?: return@produceState
        value = try { LoadState.Ready(app.container.readerRegistry.open(current)) }
        catch (t: Throwable) { LoadState.Error(t.message ?: "Не удалось открыть файл") }
    }
    val session = (load as? LoadState.Ready)?.session
    DisposableEffect(session) { onDispose { runCatching { session?.close() } } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(item?.title ?: "Читалка", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Назад") } },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val state = load) {
                LoadState.Loading -> CircularProgressIndicator()
                is LoadState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                    Button(onClick = onBack) { Text("Вернуться в библиотеку") }
                }
                is LoadState.Ready -> when (val s = state.session) {
                    is TextReaderSession -> TextReader(s, app)
                    is ComicReaderSession -> ComicReader(s, app)
                    is PagedBitmapReaderSession -> PagedBitmapReader(s, app)
                    else -> Text("Неизвестный ReaderSession")
                }
            }
        }
    }
}

@Composable
private fun TextReader(session: TextReaderSession, app: OmniReaderApplication) {
    val chapters = session.chapters
    var chapterIndex by remember(session) { mutableStateOf((session.item.currentChapter ?: 0).coerceIn(0, (chapters.size - 1).coerceAtLeast(0))) }
    val scroll = rememberScrollState()

    val savedOffset = if (chapterIndex == (session.item.currentChapter ?: 0))
        (session.item.positionOffset ?: 0L).toInt().coerceAtLeast(0) else 0
    LaunchedEffect(chapterIndex, scroll.maxValue) {
        // maxValue is 0 before the text is laid out. Re-run once layout exposes the real range.
        if (savedOffset == 0 || scroll.maxValue > 0) {
            scroll.scrollTo(savedOffset.coerceAtMost(scroll.maxValue.coerceAtLeast(0)))
        }
    }
    LaunchedEffect(chapterIndex, scroll) {
        snapshotFlow { Triple(scroll.isScrollInProgress, scroll.value, scroll.maxValue) }
            .collect { (moving, value, max) ->
                if (!moving) {
                    val local = if (max <= 0) 0f else value.toFloat() / max
                    val progress = ((chapterIndex + local) / chapters.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                    app.container.repository.saveProgress(session.item.id, chapterIndex, null, value.toLong(), progress)
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (chapterIndex > 0) chapterIndex-- }, enabled = chapterIndex > 0) { Icon(Icons.Default.ChevronLeft, "Предыдущая глава") }
            Icon(Icons.Default.MenuBook, null)
            Text(" ${chapterIndex + 1}/${chapters.size} • ${chapters[chapterIndex].title}", modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { if (chapterIndex < chapters.lastIndex) chapterIndex++ }, enabled = chapterIndex < chapters.lastIndex) { Icon(Icons.Default.ChevronRight, "Следующая глава") }
        }
        SelectionContainer {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(chapters[chapterIndex].title, style = MaterialTheme.typography.headlineSmall)
                Text(chapters[chapterIndex].text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ComicReader(session: ComicReaderSession, app: OmniReaderApplication) {
    if (session.pageCount <= 0) { Text("В архиве не найдено изображений"); return }
    var rtl by remember { mutableStateOf(true) }
    val initial = (session.item.currentPage ?: 0).coerceIn(0, session.pageCount - 1)
    val pager = rememberPagerState(initialPage = initial, pageCount = { session.pageCount })
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            app.container.repository.saveProgress(session.item.id, null, page, null, (page + 1f) / session.pageCount)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Страница ${pager.currentPage + 1}/${session.pageCount}", modifier = Modifier.weight(1f))
            IconButton(onClick = { rtl = !rtl }) { Icon(Icons.Default.SwapHoriz, if (rtl) "Справа налево" else "Слева направо") }
            Text(if (rtl) "RTL" else "LTR", style = MaterialTheme.typography.labelMedium)
        }
        HorizontalPager(
            state = pager,
            reverseLayout = rtl,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image by produceState<BitmapLoadState>(initialValue = BitmapLoadState.Loading, session, page) {
                value = try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val bytes = session.page(page)
                        decodeImageBytes(app, bytes)
                    } ?: error("Не удалось декодировать изображение")
                    BitmapLoadState.Ready(bitmap)
                } catch (t: Throwable) {
                    BitmapLoadState.Error(t.message ?: "Не удалось открыть страницу")
                }
            }
            when (val state = image) {
                BitmapLoadState.Loading -> CircularProgressIndicator()
                is BitmapLoadState.Ready -> ZoomableBitmap(state.bitmap)
                is BitmapLoadState.Error -> Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

@Composable
private fun PagedBitmapReader(session: PagedBitmapReaderSession, app: OmniReaderApplication) {
    if (session.pageCount <= 0) { Text("Документ не содержит страниц"); return }
    val initial = (session.item.currentPage ?: 0).coerceIn(0, session.pageCount - 1)
    val pager = rememberPagerState(initialPage = initial, pageCount = { session.pageCount })
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            app.container.repository.saveProgress(session.item.id, null, page, null, (page + 1f) / session.pageCount)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Страница ${pager.currentPage + 1}/${session.pageCount}", modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center)
        VerticalPager(state = pager, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
            val image by produceState<BitmapLoadState>(initialValue = BitmapLoadState.Loading, session, page) {
                value = try {
                    BitmapLoadState.Ready(session.render(page, 1200))
                } catch (t: Throwable) {
                    BitmapLoadState.Error(t.message ?: "Не удалось отрисовать страницу")
                }
            }
            when (val state = image) {
                BitmapLoadState.Loading -> CircularProgressIndicator()
                is BitmapLoadState.Ready -> ZoomableBitmap(state.bitmap)
                is BitmapLoadState.Error -> Text(state.message, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        }
    }
}

private fun decodeImageBytes(context: Context, bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    val isTiff = bytes.size >= 4 && (
        (bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() && (bytes[2] == 0x2A.toByte() || bytes[2] == 0x2B.toByte()) && bytes[3] == 0x00.toByte()) ||
        (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() && bytes[2] == 0x00.toByte() && (bytes[3] == 0x2A.toByte() || bytes[3] == 0x2B.toByte()))
    )
    if (isTiff) {
        val temp = File.createTempFile("comic-page-", ".tiff", context.cacheDir)
        return try {
            temp.outputStream().use { it.write(bytes) }
            TiffCoder.decode(temp, 0)
        } finally {
            temp.delete()
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val decoded = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
        if (decoded != null) return decoded
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

@Composable
private fun ZoomableBitmap(bitmap: Bitmap?) {
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap == null) CircularProgressIndicator()
        else Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(bitmap) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                offset = Offset(
                                    x = (size.width / 2f - tap.x) * 1.5f,
                                    y = (size.height / 2f - tap.y) * 1.5f,
                                )
                            }
                        },
                    )
                }
                .pointerInput(bitmap, scale) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        scale = next
                        offset = if (next <= 1.01f) Offset.Zero else offset + pan
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
