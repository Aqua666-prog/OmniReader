package app.omnireader.android.scanner

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.omnireader.android.core.model.FileFormat
import app.omnireader.android.core.util.NaturalSort
import app.omnireader.android.data.db.LibraryItemEntity
import app.omnireader.android.data.repository.LibraryRepository
import app.omnireader.android.metadata.MetadataExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

class LibraryScanner(
    private val context: Context,
    private val repository: LibraryRepository,
    private val metadata: MetadataExtractor,
) {
    data class ScanState(
        val running: Boolean = false,
        val sourceName: String? = null,
        val scanned: Int = 0,
        val found: Int = 0,
        val currentFile: String? = null,
        val error: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private var job: Job? = null

    fun scanAll() {
        job?.cancel()
        job = scope.launch {
            repository.getFolders().forEach { folder ->
                coroutineContext.ensureActive()
                scanSource(Uri.parse(folder.uri), folder.displayName)
            }
            _state.value = _state.value.copy(running = false, currentFile = null)
        }
    }

    fun scan(uri: Uri, displayName: String) {
        job?.cancel()
        job = scope.launch {
            scanSource(uri, displayName)
            _state.value = _state.value.copy(running = false, currentFile = null)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false, currentFile = null)
    }

    private suspend fun scanSource(uri: Uri, displayName: String) = withContext(Dispatchers.IO) {
        _state.value = ScanState(running = true, sourceName = displayName)
        val scanToken = UUID.randomUUID().toString()
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.exists() || !root.canRead()) {
            repository.updateSourceAvailability(uri.toString(), false)
            _state.value = _state.value.copy(error = "Папка недоступна", running = false)
            return@withContext
        }
        val detector = FormatDetector(context.contentResolver)
        try {
            walk(
                dir = root,
                onFile = { file, parentName ->
                    coroutineContext.ensureActive()
                    val name = file.name ?: return@walk
                    _state.value = _state.value.copy(scanned = _state.value.scanned + 1, currentFile = name)
                    val detection = runCatching { detector.detect(file.uri, name, file.type) }.getOrNull() ?: return@walk
                    if (detection.format == FileFormat.UNKNOWN) return@walk
                    val series = SeriesParser.parse(name, parentName)
                    val size = file.length()
                    val modified = file.lastModified()
                    val meta = metadata.extract(file.uri, detection.format, name, "$modified:$size")
                    val item = LibraryItemEntity(
                        uri = file.uri.toString(),
                        fileName = name,
                        format = detection.format,
                        mimeType = file.type,
                        contentType = detection.contentType,
                        title = meta.title?.takeIf(String::isNotBlank) ?: series.title,
                        author = meta.author,
                        series = series.series,
                        volumeNumber = series.volume,
                        description = meta.description,
                        coverCachePath = meta.coverPath,
                        fileSize = size,
                        lastModified = modified,
                        pageCount = meta.pageCount,
                        chapterCount = meta.chapterCount,
                        sourceFolderUri = uri.toString(),
                        contentFingerprint = fingerprint(file),
                        lastSeenScanToken = scanToken,
                        isPresent = true,
                    )
                    repository.upsertScanned(item)
                    _state.value = _state.value.copy(found = _state.value.found + 1)
                },
                onImageFolder = { directory, images, parentName ->
                    coroutineContext.ensureActive()
                    val name = directory.name?.takeIf(String::isNotBlank) ?: "Images"
                    _state.value = _state.value.copy(scanned = _state.value.scanned + 1, currentFile = "$name/")
                    val sorted = images.sortedWith { a, b -> NaturalSort.comparator.compare(a.name.orEmpty(), b.name.orEmpty()) }
                    val size = sorted.sumOf { it.length().coerceAtLeast(0L) }
                    val modified = sorted.maxOfOrNull { it.lastModified() } ?: directory.lastModified()
                    val meta = metadata.extractImageFolder(sorted.mapNotNull { image -> image.name?.let { it to image.uri } }, directory.uri.toString())
                    val series = SeriesParser.parse(name, parentName)
                    repository.upsertScanned(
                        LibraryItemEntity(
                            uri = directory.uri.toString(),
                            fileName = "$name/",
                            format = FileFormat.IMAGE_FOLDER,
                            mimeType = "vnd.android.document/directory",
                            contentType = FormatDetector.contentType(FileFormat.IMAGE_FOLDER),
                            title = series.title,
                            series = series.series,
                            volumeNumber = series.volume,
                            coverCachePath = meta.coverPath,
                            fileSize = size,
                            lastModified = modified,
                            pageCount = sorted.size,
                            sourceFolderUri = uri.toString(),
                            contentFingerprint = fingerprintFolder(sorted),
                            lastSeenScanToken = scanToken,
                            isPresent = true,
                        )
                    )
                    _state.value = _state.value.copy(found = _state.value.found + 1)
                },
            )
            repository.finalizeSuccessfulScan(uri.toString(), scanToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // A partial/cancelled scan must never hide files that were not reached.
            repository.updateSourceAvailability(uri.toString(), true)
            _state.value = _state.value.copy(error = t.message ?: t::class.java.simpleName)
        }
    }

    private suspend fun walk(
        dir: DocumentFile,
        onFile: suspend (DocumentFile, String?) -> Unit,
        onImageFolder: suspend (DocumentFile, List<DocumentFile>, String?) -> Unit,
        parentDirectoryName: String? = null,
    ) {
        coroutineContext.ensureActive()
        val children = runCatching { dir.listFiles() }.getOrElse { emptyArray() }
        val directImages = children.filter { it.isFile && FormatDetector.isImageName(it.name.orEmpty()) }
        val groupedImages = directImages.size >= 2
        if (groupedImages) onImageFolder(dir, directImages, parentDirectoryName)

        val currentName = dir.name
        for (child in children) {
            coroutineContext.ensureActive()
            when {
                child.isDirectory -> walk(child, onFile, onImageFolder, currentName)
                child.isFile && !(groupedImages && child in directImages) -> onFile(child, currentName)
            }
        }
    }

    private fun fingerprint(file: DocumentFile): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(file.length().toString().toByteArray())
        digest.update(':'.code.toByte())
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var remaining = buffer.size
            while (remaining > 0) {
                val n = input.read(buffer, 0, remaining)
                if (n <= 0) break
                digest.update(buffer, 0, n)
                remaining -= n
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fingerprintFolder(images: List<DocumentFile>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        images.sortedWith { a, b -> NaturalSort.comparator.compare(a.name.orEmpty(), b.name.orEmpty()) }.forEach { file ->
            digest.update(file.name.orEmpty().toByteArray())
            digest.update(0.toByte())
            digest.update(file.length().toString().toByteArray())
            digest.update(0.toByte())
            digest.update(file.lastModified().toString().toByteArray())
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
