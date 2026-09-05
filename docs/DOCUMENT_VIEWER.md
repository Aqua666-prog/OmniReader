# OmniReader 0.8.0 — PDF/DjVu Document Viewer

Статус: завершённый кандидат исходников после восстановления 0.7.0. APK по просьбе пользователя не собирался. Полный Android compile/lint/instrumented tests и device QA остаются обязательными перед релизом.

## Что реализовано

- Отдельный fixed-layout viewer для PDF/DjVu; старый `ReaderScreen` и reflowable-парсеры не заменялись.
- PDFium region rendering и glyph coordinates; DjVuLibre region rendering/word zones.
- Ленивая отрисовка: preview + видимые 512×512 tiles, LRU 32 MiB, отмена устаревших поколений и дедупликация одинаковых render-errors.
- Continuous / Page / Spread, pinch до 15×, XY pan, fling, double-tap, сохранение нормализованного якоря страницы и масштаба.
- Нативный текст всегда имеет приоритет. Если text layer пуст, `DocumentSession` рендерит ограниченный OCR-raster (до ~3 MP / 2400 px по длинной стороне) и запускает офлайн Tesseract.
- OCR: Tesseract4Android 4.9.0 / Tesseract 5.5.1, модели `eng+rus+heb+yid+ara`. Модели загружаются только во время build, pin-ятся commit/hash и упаковываются в assets; runtime network не нужен. Один native OCR engine переиспользуется и освобождается после простоя/при memory pressure.
- OCR-кэш `document-ocr-v2`: координаты в document space, AtomicFile, 8 MiB/page, 64 MiB process cache, ключ по URI+ревизии; ненадёжные content-provider revisions не переиспользуются между сессиями.
- Long press + два маркера; диапазон может охватывать несколько страниц. Порядок берётся из backend UTF-16, без визуального разворота RTL. В Continuous режиме маркер у края автоматически прокручивает документ. Clipboard-range ограничен 200 000 UTF-16 code units с учётом межстраничных разделителей.
- Поиск использует тот же native/OCR text. Видимый список ограничен 1000 hits, но Previous/Next работают потоково по всему документу, включая 2001+ страниц, wrap-around и результаты за пределами списка. До 10 отдельных нечитаемых страниц могут быть пропущены; потеря permissions не скрывается.
- Search snippet не разрывает surrogate pair. Добавлены проверки русского, Hebrew/Yiddish, Arabic и emoji.
- Многостраничные заметки/цитаты сохраняются без schema bump: одна `document_marks` row содержит v2 JSON со страницами/rects; старый v1 payload продолжает читаться.
- Отдельные generation tokens не дают старой selection/search операции сбросить состояние новой. Ошибки OCR cache write не ломают текущее распознавание.

## Объективные ограничения

- Полная Android-компиляция и instrumented tests в этой среде не выполнены: Gradle/Android SDK отсутствуют. Это не маскируется как успешная сборка.
- В режиме `PAGE` layout содержит только текущую страницу: межстраничный диапазон сохраняется/отображается после перехода, но drag через невидимую соседнюю страницу удобнее выполнять в `CONTINUOUS`. В `SPREAD` можно тянуть между двумя видимыми страницами.
- Native render сам по себе нельзя прервать посередине; устаревший результат отбрасывается после возврата native call.
- PDF-страница с text layer >250 000 символов отклоняется защитным лимитом памяти.
- DjVu точность выделения зависит от наличия word zones в самом файле.
- Password/encrypted PDF, формы, редактирование PDF и встроенные document links этой работой не добавлялись.
- Старый import/TTS pipeline PDF остаётся отдельным от интерактивного viewer; новый OCR автоматически улучшает поиск/selection viewer, но не переписывает старый TTS importer.
- Реальная резкость 10×/15×, crop/rotation mapping, память на слабых устройствах и 16 KB native packaging должны быть подтверждены device/CI QA.

## Локальные проверки без Android toolchain

- `python3 tools/verify-source.py`
- `python3 tools/test-document-migration.py`
- Компиляция чистых Kotlin-компонентов `DocumentGeometry`, `DocumentTextLayer`, `DocumentSearchNavigator`, `DocumentErrors` через `kotlinc` со stub `android.graphics.Bitmap`.

Полный CI должен выполнить Gradle unit tests, lint, assemble, `tools/audit-native-apk.py` и `zipalign -P 16`; instrumented tests — на эмуляторе/устройстве. Device matrix находится в `docs/DOCUMENT_QA.md`.
