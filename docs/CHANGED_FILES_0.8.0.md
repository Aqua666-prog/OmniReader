# Изменения 0.8.0 поверх 0.7.0

Основные исходники:

- `document/DocumentTextLayer.kt` — multi-page selection model/range, Unicode-safe snippets, bounded selection.
- `document/DocumentSearchNavigator.kt` — streaming prev/next, wrap, unreadable-page handling.
- `document/DocumentBackend.kt` — native-text-first, OCR fallback, bounded OCR raster, cancellation/cache integration.
- `document/DocumentTextStore.kt` — revision-aware AtomicFile OCR cache.
- `document/TesseractOcrEngine.kt` — offline OCR, reusable native engine, idle/memory cleanup.
- `document/DocumentErrors.kt` — user-facing document/OCR errors.
- `document/DocumentPersistence.kt` — multi-page mark geometry v2 with v1 compatibility.
- `ui/document/DocumentSurfaceView.kt` — cross-page selection, edge autoscroll, generation/race fixes.
- `ui/document/DocumentSearchSheet.kt` — OCR search, streaming navigation, cancellation/error state.
- `ui/document/DocumentTileRenderer.kt` — stale-generation/error dedup fixes.
- `ui/document/DocumentViewer.kt`, `DocumentSettings.kt` — OCR/selection UI integration.
- `ReaderApplication.kt` — OCR engine cleanup on memory pressure.
- `app/build.gradle.kts`, `settings.gradle.kts` — Tesseract dependency, JitPack, pinned tessdata build assets.

Tests:

- `DocumentRangeSearchTest.kt` — multi-page/RTL/Unicode, cancellation, 2001 pages, >500 matches, wrap regression, Clipboard limit.
- `DocumentOcrTest.kt` — native text bypass, OCR cache, real offline engine, cancellation.
- `PdfBackendTest.kt` — Unicode PDF assertions.

Build/docs/CI updated for 0.8.0. Historical 0.7.0 validation files are kept as historical records.
