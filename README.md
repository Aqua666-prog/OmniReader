# OmniReader

OmniReader is an offline-first universal Android reader for local books, manga, comics, documents, scans and image folders. It does not require an account or server. Original files remain in their SAF-selected folders; Room is the source of truth for the local library and reading progress.

## Architecture

Conventional Android Gradle project: Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, Room, Navigation Compose and Android Storage Access Framework. No `MANAGE_EXTERNAL_STORAGE`, no mandatory server and no `runBlocking` in application code.

Data path:

`SAF roots → recursive LibraryScanner → magic/MIME/container detection → metadata + thumbnail → Room → library UI → ReaderRegistry → ReaderProvider → progress back to Room`

Reader implementations are isolated by content family. A broken optional decoder can therefore be removed without rewriting the scanner, database or other readers.

## Reader providers

- `TextReaderProvider` — EPUB/EPUB3, FB2, FB2.ZIP, TXT, HTML/HTM/XHTML, Markdown.
- `DocumentTextReaderProvider` — DOCX, ODT and RTF semantic/local text extraction.
- `KindleReaderProvider` — DRM-free MOBI/AZW3 with uncompressed/PalmDOC text records. DRM and HUFF/CDIC currently fail explicitly instead of crashing.
- `ComicReaderProvider` — CBZ/ZIP, CBR/RAR, CB7/7Z, CBT/TAR and virtual image folders. Natural sorting, on-demand pages and true RTL/LTR pager direction.
- `PdfReaderProvider` — Android `PdfRenderer`, using temporary SAF staging only when the source descriptor is not seekable.
- `DjvuReaderProvider` — DJVU/DJV through an isolated DjVuLibre-based decoder.
- `ImageReaderProvider` — JPG/JPEG, PNG, WEBP, AVIF, GIF, BMP and multi-page TIFF/TIF.

PDF, DJVU and TIFF share the `PagedBitmapReaderSession` abstraction. Comic archives and image folders share `ComicReaderSession`.

## Supported formats

| Family | Formats | Status / boundary |
|---|---|---|
| Reflowable books | EPUB, EPUB3 | Local OPF/spine parsing, metadata/cover, chapters |
| FictionBook | FB2, FB2.ZIP | Local XML parsing, metadata/cover, chapters |
| Plain/web text | TXT, HTML, HTM, XHTML, MD/Markdown | Local reader; TXT encoding detection |
| Rich/office text | RTF, DOCX, ODT | Local semantic text extraction; not pixel-perfect Word/Writer rendering |
| Kindle | MOBI, AZW3 | DRM-free PalmDOC/uncompressed text; HUFF/CDIC/DRM are explicit limitations |
| Fixed documents | PDF | Native page rendering, zoom, page resume |
| Scans | DJVU, DJV | Local native decoder, page rendering/resume |
| ZIP comics | CBZ, ZIP | Lazy page reads, natural sort |
| RAR comics | CBR, RAR | Junrar, serialized lazy extraction |
| 7z comics | CB7, 7Z | Commons Compress, lazy page extraction |
| TAR comics | CBT | Streaming TAR page access |
| Images | JPG, JPEG, PNG, WEBP, AVIF, GIF, BMP | Standalone image reader; AVIF depends on Android platform ImageDecoder support |
| TIFF | TIFF, TIF | Native multi-page decoder |
| Image folders | folders containing 2+ supported images | Collapsed into one virtual manga/comic item rather than hundreds of library rows |

Archives are not permanently unpacked. Readers stage only when an API requires a random-access local `File`; the bounded cache is disposable and versioned by file metadata.

### Comic page image formats

Archive/folder pages are recognized as JPG/JPEG/PNG/WEBP/AVIF/GIF/BMP/TIFF/TIF. The UI uses Android `ImageDecoder` where available, `BitmapFactory` as fallback, and the TIFF decoder for TIFF pages.

## Format detection

Detection does not trust extensions alone. It combines:

1. extension;
2. MIME type;
3. magic bytes/signatures;
4. ZIP/container structure.

Examples: ZIP containing `META-INF/container.xml`/EPUB mimetype is EPUB; ZIP containing `word/` is DOCX; ZIP containing `.fb2` is FB2.ZIP; ZIP dominated by images is treated as CBZ/comic content. RAR, 7z, PDF, DJVU, MOBI, TAR and common image signatures are recognized directly.

## Text encodings

TXT supports UTF-8, UTF-8 BOM, UTF-16 LE/BE and heuristic fallback among Windows-1251, Windows-1252, KOI8-R, ISO-8859-1 and ISO-8859-5. Room already contains a per-item encoding override field for a future explicit chooser UI.

## Library scanner

The user may add multiple roots with `OpenDocumentTree`; persistable URI permissions are retained. The scanner is recursive, cancellable and asynchronous.

Every successful scan has a generation token. Existing rows are only marked missing after the entire traversal completes, so cancellation cannot make an incompletely scanned half-library disappear. Fingerprints reconnect moved/renamed files when possible and preserve reading progress/status/user metadata.

Directories with two or more direct supported images are represented as `IMAGE_FOLDER` virtual manga items and their individual image files are not separately added to the same library level.

## Progress and database

Room stores chapter/page/offset plus normalized progress. Text readers restore chapter and scroll offset; comic/PDF/DJVU/TIFF readers restore page. Rescanning does not reset reading state.

The schema also contains bookmarks, notes, collections, tags and reading-session records so those UIs can be added without replacing the persistence layer.

## UI

The 0.1.0 source release includes library grid/list, search, folder management, scanner progress/cancel, text reader, paged document/image reader and comic reader with true RTL/LTR switching, pinch zoom and double-tap zoom.

The current text pipeline prioritizes deterministic offline reading over exact CSS/Word layout fidelity. Advanced typography, page-turn text pagination, two-page comics, Webtoon mode, full notes/quotes UI, statistics and advanced series management remain later UX work rather than format blockers.

## Tests

Source tests cover:

- natural page sorting;
- series/volume parsing;
- supported extension recognition;
- text encoding detection;
- RTF extraction;
- PalmDOC decompression primitive;
- scanner-style Room rescan/move merge and progress preservation;
- reading progress/status calculation.

The release gate remains:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

## Building

Read `BUILD.md` and `RELEASE_NOTES.md`. This generated archive also includes `bootstrap-wrapper.sh` and `bootstrap-wrapper.ps1`. If `gradle/wrapper/gradle-wrapper.jar` is absent, run the matching bootstrap script once; it downloads the official Gradle 9.5.0 wrapper JAR and verifies its published SHA-256 before installation.

GitHub Actions: `.github/workflows/build-apk.yml` performs the same bootstrap automatically, then tests, lints and assembles the debug APK.

## Known limitations

- MOBI/AZW3: DRM is intentionally unsupported. HUFF/CDIC/KF8 reconstruction is not yet implemented by the internal parser; such books return an explicit unsupported-compression error.
- AVIF: platform decoding depends on Android version/device codec support; the app fails gracefully where the platform cannot decode it.
- DOCX/ODT/RTF: semantic reading text, not pixel-exact office-layout rendering; complex equations, charts, floating objects and macros are not reproduced.
- EPUB: native deterministic text extraction does not reproduce every CSS/fixed-layout EPUB feature.
- PDF text search/outline UI, advanced bookmarks/notes UI, Webtoon/two-page comic modes and statistics are outside this format-expansion pass.
- The generation environment did not have an Android SDK/networked Gradle build environment, so this source release is not an APK-validated release until you run the build gate yourself. See `BUILD_STATUS.md`.
