# Build note

## Reader 0.4.0

В текущем рабочем контейнере **нет Android SDK и Gradle distribution**, поэтому полноценные Android-задачи `testDebugUnitTest` и `assembleDebug` локально не запускались. APK из этой среды не заявляется собранным.

### Что реально проверено в контейнере

- структура Android/Compose source tree;
- XML-манифест и build-конфигурация просмотрены после изменений;
- Kotlin source tree прошёл лексическую проверку баланса скобок, строк и комментариев;
- ключевые изменённые Android/Compose-файлы подавались в `kotlinc` для parser-level проверки; Android/Compose unresolved references без Android classpath ожидаемы, parser-level ошибок не обнаружено в завершённых проверках;
- чистые Kotlin-модели и утилиты (`ReaderModels`, `PageChunker`, `SeriesInference`, `TextUtil`) реально скомпилированы `kotlinc`;
- smoke test подтвердил новый `PDF_TEXT`, пагинацию legacy-core, подсчёт слов и распознавание кириллического `Том 6`;
- новый `ExactPaginator` отдельно скомпилирован против минимальных API-совместимых Compose-stubs, включая `TextMeasurer`, `TextLayoutResult`, `Constraints` и Float line bottoms;
- добавлен JVM-тест `ExactPaginatorPositionTest` для восстановления страницы по `block + offset` и сопоставления скрытого PDF text layer с предыдущей визуальной страницей;
- Room schema поднята до v4 и содержит явную миграцию `3 -> 4` для `books.positionOffset`;
- platform PDF text extraction защищён runtime-проверкой `SDK_INT >= 35`;
- TTS sleep timer отменяется при новом запуске книги и при Stop;
- HTML export экранирует пользовательский текст;
- перед выдачей исходный ZIP проверяется командой `unzip -t`.

### Что обязательно проверить настоящей Android-сборкой

CI или локальный Android SDK должен проверить:

1. реальные Compose signatures для `TextMeasurer`/pagination;
2. Room schema validation и миграции 1 -> 2 -> 3 -> 4;
3. Android 15+ `PdfRenderer.Page.getTextContents()` на настоящем `android.jar`;
4. `UtteranceProgressListener.onRangeStart` и MediaSession/TTS lifecycle;
5. Compose selection + PDF text bottom sheet;
6. unit tests;
7. `assembleDebug`.

Для этого проект содержит `.github/workflows/android.yml`:

1. Android SDK platform 37;
2. Gradle 9.5.0;
3. `gradle testDebugUnitTest --stacktrace`;
4. `gradle assembleDebug --stacktrace`;
5. upload `app-debug.apk` как GitHub Actions artifact.

## Build configuration note

AGP 9.x включает built-in Kotlin/new DSL, а этот source slice пока использует `org.jetbrains.kotlin.android` + `org.jetbrains.kotlin.kapt`. Поэтому `gradle.properties` временно содержит:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Дальнейшая техническая уборка: перевести Room processing на KSP/совместимую AGP 9 схему и убрать compatibility flags.
