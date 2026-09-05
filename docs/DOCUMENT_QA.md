# Document Viewer 0.8.0 — device QA (ещё не выполнен)

Проверять arm64 Android 16 / 16 KB, Android 8+ / 4 KB, portrait/landscape, телефон и планшет. Фиксировать модель/API/ABI/page size и SHA-256 APK.

Обязательные сценарии:

- текстовый PDF: fit width → 10×/15× → pan → rerender → выделение → Clipboard → поиск/Previous/Next;
- скан без text layer: автоматический OCR, поиск, выделение, повторное открытие с OCR-cache, отмена поиска;
- mixed Unicode: русский + Hebrew/Yiddish + Arabic + emoji; сравнить скопированный backend-order текст;
- межстраничное выделение в Continuous, reverse drag, edge autoscroll, заметка/цитата на 2+ страницах и восстановление highlights после reopen;
- 600 и 2000+ страниц: jump в конец, быстрый fling, search wrap, hits за пределами первых 1000, bounded memory;
- повреждённая страница среди читаемых, потеря SAF permission, удалённый файл, пустой документ, password/encrypted PDF;
- crop/rotation, две колонки, таблицы/изображения; проверить совпадение glyph boxes с raster;
- DjVu photo/bitonal/text: region y-mapping и word zones;
- поворот/фон/возврат: page/center/zoom восстанавливаются, старые jobs не сбрасывают busy новой операции;
- EPUB/FB2/TXT/HTML/MD/RTF/DOCX/ODT/MOBI/комиксы: прежний ReaderScreen без регрессий.

CI отдельно должен пройти unit tests, lint, Android compile, native 16 KB audit и `zipalign -P 16`.
