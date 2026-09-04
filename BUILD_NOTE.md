# Build note

## Reader 0.5.0

В текущем рабочем контейнере **нет Android SDK и Gradle distribution**, поэтому полноценные Android-задачи `testDebugUnitTest` и `assembleDebug` локально не запускались. APK из этой среды не заявляется собранным.

### Что реально проверено в контейнере

- структура Android/Compose source tree;
- XML-манифест и resource XML проходят parser-level проверку; build-конфигурация просмотрена после изменений;
- весь Kotlin/Gradle Kotlin DSL tree прошёл PSI parser-level проверку (`44` файла `.kt/.kts`, `0` синтаксических ошибок на момент упаковки);
- ключевые изменённые Android/Compose-файлы подавались в `kotlinc` для parser-level проверки; Android/Compose unresolved references без Android classpath ожидаемы, parser-level ошибок не обнаружено в завершённых проверках;
- чистые Kotlin-модели и утилиты (`ReaderModels`, `PageChunker`, `SeriesInference`, `TextUtil`) реально скомпилированы `kotlinc`;
- smoke test подтверждает core-модели/утилиты, пагинацию legacy-core, подсчёт слов и распознавание кириллического `Том 6`;
- новый `ExactPaginator` отдельно скомпилирован против минимальных API-совместимых Compose-stubs, включая `TextMeasurer`, `TextLayoutResult`, `Constraints` и Float line bottoms;
- добавлен JVM-тест `ExactPaginatorPositionTest` для восстановления страницы по `block + offset` и сопоставления скрытого fixed-layout text layer (PDF/DjVu) с предыдущей визуальной страницей;
- Room schema поднята до v4 и содержит явную миграцию `3 -> 4` для `books.positionOffset`;
- platform PDF text extraction защищён runtime-проверкой `SDK_INT >= 35`; DjVu text extraction использует embedded text zones DjVuLibre и мягко откатывается к raster-only странице, если текста нет;
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

1. Android SDK platform 36 + build-tools 36.0.0;
2. Gradle 9.5.0;
3. `gradle prepareDjvuAar --stacktrace` с проверкой pinned Git blob SHA-1;
4. `gradle testDebugUnitTest --stacktrace`;
5. `gradle assembleDebug --stacktrace`;
6. upload `app-debug.apk` как GitHub Actions artifact.

## Build configuration note

AGP 9.x включает built-in Kotlin/new DSL, а этот source slice пока использует `org.jetbrains.kotlin.android` + KSP. Поэтому `gradle.properties` временно содержит:

```properties
android.builtInKotlin=false
android.newDsl=false
```

Room compiler уже переведён на KSP `2.3.10`. Дальнейшая техническая уборка — перейти на полностью built-in Kotlin/new DSL AGP 9 и после этого убрать временные compatibility flags.

### 0.5.0 additional dependencies

JitPack is enabled for jchmlib. DjVuLibre is pinned to commit `5b9bd591befc528268cf00c28e8fb81bc75d664b`; `prepareDjvuAar` downloads that commit's prebuilt AAR from GitHub and verifies the Git blob SHA-1 `bde3f2e2cbe693343e5180b69e80b5580b40ecd4` before the build uses it. This avoids rebuilding the native DjVu stack inside JitPack. Archive support uses Junrar and Apache Commons Compress. MOBI/AZW/AZW3 support is DRM-free only. DjVu search/TTS/selection depend on an embedded text layer; raster viewing works without it.
