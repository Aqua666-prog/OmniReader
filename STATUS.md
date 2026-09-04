# Статус разработки

## 0.4.0 — точная пагинация, PDF text layer, TTS-follow, sleep timer, TXT/HTML export

### Библиотека и импорт
- [x] Android project / Compose
- [x] Room library database
- [x] DataStore settings
- [x] EPUB parser: metadata / cover / spine / text
- [x] EPUB raster images
- [x] EPUB basic footnote blocks
- [x] EPUB improved `<br>` handling and row-wise table flattening
- [x] FB2 parser
- [x] TXT parser
- [x] PDF import + first-page cover
- [x] PDF platform text extraction on Android 15+ (API 35+)
- [x] SAF import
- [x] folder scanning
- [x] internal-copy import mode
- [x] library list/grid
- [x] current/favorite/want/finished sections
- [x] authors/series/collections/formats/folders grouping
- [x] series/volume inference
- [x] book details + metadata editing

### Чтение
- [x] vertical reader
- [x] horizontal paged reader
- [x] typography-aware Compose `TextMeasurer` pagination
- [x] measured line-end splitting for oversized paragraphs
- [x] exact source offsets preserved across page fragments
- [x] exact paged-position restore with `positionOffset`
- [x] EPUB inline raster images
- [x] EPUB footnote blocks
- [x] PDF page renderer
- [x] PDF hidden/searchable/speakable text layer (API 35+)
- [x] PDF «Текст страницы» selectable sheet
- [x] reading progress
- [x] table of contents
- [x] bookmarks
- [x] EPUB/FB2/TXT search + exact paged jump to match offset
- [x] PDF search when platform text is available
- [x] custom `.ttf/.otf` fonts
- [x] per-book typography/theme/mode profile
- [x] jump from research center to saved location
- [ ] arbitrary EPUB CSS/layout reproduction
- [ ] SVG rendering
- [ ] MathML
- [ ] interactive EPUB footnote backlink navigation
- [ ] geometric PDF selection directly over raster page

### Работа с текстом
- [x] native text selection
- [x] contextual action bar
- [x] simple / extended menu modes
- [x] color highlights
- [x] quotes
- [x] notes
- [x] persistent highlights
- [x] copy / share
- [x] translator / web search / dictionary URL templates
- [x] personal dictionary
- [x] PDF quote/note/highlight through extracted page text (API 35+)

### Исследовательский центр
- [x] Quotes tab
- [x] Notes tab
- [x] Bookmarks tab
- [x] Dictionary tab
- [x] global search
- [x] delete/edit operations
- [x] open source book at saved block
- [x] Markdown export
- [x] TXT export
- [x] HTML export

### TTS
- [x] Android TextToSpeech foreground service
- [x] background / screen-off playback
- [x] sequential text-block reading
- [x] PDF text-layer reading on API 35+
- [x] Pause / Resume / Stop
- [x] Previous / Next
- [x] starts from current reading position
- [x] rate 0.1x–4x
- [x] pitch 0.5x–2x
- [x] persisted reading progress
- [x] MediaSession
- [x] system/Bluetooth controls
- [x] `onRangeStart` live spoken-range highlighting
- [x] paged reader automatically follows TTS inside long paragraphs
- [x] sleep timer: 15/30/45/60/90 minutes + off
- [ ] per-voice speed/pitch profiles
- [ ] advanced audio-focus policy

### Backup / Restore
- [x] ZIP backup
- [x] Room checkpoint
- [x] database export
- [x] DataStore export
- [x] internal books/covers/fonts/EPUB assets
- [x] ZIP traversal protection
- [x] staged restore before Room/DataStore startup
- [ ] selective restore
- [ ] encrypted backup
- [ ] cloud sync

### Данные
- [x] Room schema v4
- [x] migration 1 -> 2
- [x] migration 2 -> 3
- [x] migration 3 -> 4 (`books.positionOffset`)
- [x] paragraph kind/resource path
- [x] `PDF_TEXT` block kind without a schema change to paragraphs
- [x] book reading profiles
- [x] CI workflow

## Следующий кодовый слой
- [ ] EPUB link graph + interactive noteref/backlinks
- [ ] SVG / MathML / richer tables
- [ ] PDF geometric text selection directly on the rendered page
- [ ] PDF search-match overlays
- [ ] TTS voice profiles + refined audio focus
- [ ] encrypted/selective backup
- [ ] cloud/WebDAV sync
- [ ] tablet/two-column reader
