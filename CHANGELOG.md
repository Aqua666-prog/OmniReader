# 0.6.0 — библиотека и комфорт чтения

Переработана библиотека, карточки, темы, меню чтения и настройки. Добавлены сортировки, поиск по нескольким словам, асинхронные обложки, яркость окна, управление гашением экрана, маршрут открытия внешних файлов, обработка ошибок загрузки и защита восстановления позиции от временных настроек по умолчанию. Новые тесты фильтрации добавлены, но не запускались в текущей среде. Полный список — в README.

Android-сборка 0.6.0 в этой сессии не выполнена.

---

# Changelog

## 0.5.0

- Extended import: FB2.ZIP/FB2.GZ, HTML/XHTML, Markdown, RTF, DOCX, ODT, DRM-free MOBI/AZW/AZW3, ZIP, CBZ/CBR/CB7, CHM and DjVu/DJV.
- Native DjVu page rendering via a pinned DjVuLibre fork with Android 16 / 16 KB page-size support.
- Embedded DjVu text layers are imported as hidden `DJVU_TEXT` blocks for search, TTS, quotes, notes and selectable «Текст страницы».
- EPUB internal chapter links, redesigned navigation sheet and chapter-local page progress.
- Full-screen image viewer with Save As support.
- Reader bars can be toggled with a page tap; faster typography controls.
- TTS global enable/disable plus explicit Stop action.
- Material 3 polish for library cards, reader controls and navigation.
- App version bumped to 0.5.0 / versionCode 5.


## 0.4.0

### Added
- Compose `TextMeasurer`-driven exact reflowable pagination.
- Source-offset page slices so long paragraphs can span multiple physical pages without losing annotation coordinates.
- Room `books.positionOffset` and migration 3 -> 4 for exact paged-position restore.
- Android 15+ (`PdfRenderer.Page.getTextContents`) PDF text extraction.
- Hidden `PDF_TEXT` reader blocks used for PDF search, TTS and research annotations.
- Selectable PDF «Текст страницы» sheet; quotes, highlights, notes, dictionary/translation actions work on extracted text.
- TTS spoken-range highlighting using `UtteranceProgressListener.onRangeStart`.
- TTS follow mode that flips exact pages as the spoken range crosses a long paragraph.
- TTS sleep timer presets: 15/30/45/60/90 minutes and cancel.
- TXT research export.
- Standalone HTML research export with UTF-8 and escaped user text.
- Improved EPUB handling for `<br>` and table rows.

### Changed
- Replaced the runtime heuristic page budget with measured Compose line layout in the active font/size/width.
- PDF search is enabled when an extracted platform text layer exists.
- PDF text blocks no longer distort visual page counts/slider navigation.
- Reader progress can persist an offset inside a block.
- Search-result navigation now preserves the first match offset and opens the measured page fragment instead of only the paragraph start.
- App version bumped to 0.4.0 / versionCode 4.

### Compatibility / known limitations
- Platform PDF text extraction requires Android 15 / API 35 or newer; older Android versions keep raster PDF viewing.
- PDF text selection currently happens in the page text sheet, not geometrically on top of glyphs in the raster page.
- EPUB arbitrary CSS, SVG, MathML and interactive note backlinks are not finished.
- Exact pagination measures text, but image/PDF pages are still isolated media pages rather than CSS layout boxes.

## 0.3.0

### Added
- User `.ttf/.otf` font import, private font storage and font selection.
- Live custom-font catalog available to settings and reader screens.
- Room-backed per-book reading profiles.
- Vertical / horizontal-paged reader mode setting.
- Pure Kotlin `PageChunker` heuristic pagination engine and tests.
- EPUB raster image extraction and inline rendering.
- Basic EPUB footnote block extraction/rendering.
- Resource-aware reader blocks (`PARAGRAPH`, `IMAGE`, `FOOTNOTE`, `PDF_PAGE`).
- PDF parser/import path using Android `PdfRenderer`.
- PDF first-page cover generation.
- PDF page rendering in vertical and paged readers.
- Android framework `MediaSession` for TTS.
- Previous / Next TTS transport actions.
- System/Bluetooth media-control handling.
- Local ZIP backup of database, settings, books, covers, fonts and EPUB assets.
- Safe staged restore applied before Room/DataStore startup on next process launch.

### Changed
- Room schema bumped to version 3.
- `paragraphs` now stores block kind and optional resource path.
- Reader settings now include custom font path and reader mode.
- Reader uses an effective settings flow composed from global settings plus an optional per-book profile.
- EPUB chapters can preserve an ordered mixture of text and image/footnote elements.
- TTS skips non-speakable image/PDF blocks.
- App version bumped to 0.3.0 / versionCode 3.

### Known limitations
- Reflowable horizontal pagination is heuristic rather than pixel-exact.
- EPUB renderer does not yet reproduce arbitrary CSS, SVG, tables, MathML or interactive note backlinks.
- PDF is raster-page only; no text extraction, selection, search, annotations or TTS yet.
- Restore is staged and requires a full app restart.
- No cloud sync yet.

## 0.2.0

### Added
- Room `annotations` and `dictionary_entries` tables with migration 1 -> 2.
- Color highlights, quotes and notes attached to exact text ranges.
- Persistent highlight rendering inside the reader.
- Simple / Extended contextual text-action modes.
- Personal dictionary with source context.
- Configurable external dictionary, translator and web-search URL templates.
- Global research center: Quotes / Notes / Bookmarks / Dictionary.
- Search and Markdown export in the research center.
- Jump back from a saved research item to its source location in the book.
- Foreground Android TextToSpeech service.
- Background / screen-off TTS playback.
- TTS pause/resume/stop notification controls.
- TTS rate 0.1x–4x and pitch 0.5x–2x.
- Reading-progress persistence while TTS is running.

### Changed
- App version bumped to 0.2.0 / versionCode 2.
- Reader navigation accepts an explicit initial block for research-center jumps.
- Reader settings include text-action and TTS options.
- AGP 9.x compatibility explicitly opts out of built-in Kotlin/new DSL while using `kotlin-android` + `kapt`.
