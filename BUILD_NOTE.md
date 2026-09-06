# OmniReader 0.8.0 — build status

Это архив исходников после восстановления и завершения работ поверх 0.7.0. По просьбе пользователя APK в этой сессии **не собирался**.

Что проверено локально без Android toolchain:

- `python3 tools/verify-source.py` — XML, лексическая целостность Kotlin/KTS, дубли imports, версия;
- `python3 tools/test-document-migration.py` — миграция Room 4→5, Unicode и foreign keys;
- чистая Kotlin-часть DocumentGeometry / DocumentTextLayer / DocumentSearchNavigator / DocumentErrors компилируется `kotlinc` со служебными Android-stub типами.
- отдельный pure-Kotlin smoke проверяет wrap-around поиска, навигацию за пределами 500 результатов, Unicode/RTL межстраничное выделение и лимит Clipboard с межстраничным разделителем.

В текущей среде нет Gradle и Android SDK, поэтому полный Android compile, lint, JVM Gradle tests и instrumented tests здесь не запускались. Это честно оставлено на штатный GitHub Actions/Android environment; CI также проверяет фактически упакованные `.so` на 16 KB page-size compatibility.

Подробности реализации и оставшиеся device-level проверки: `docs/DOCUMENT_VIEWER.md` и `docs/DOCUMENT_QA.md`.
## Исправление CI для Android 17 / API 37

`io.legere:pdfiumandroid:2.0.3` требует `compileSdk >= 37`. При этом Google публикует платформу API 37 для `sdkmanager` под идентификатором `platforms;android-37.0`, а не `platforms;android-37`. Workflow исправлен на `compileSdk = 37`, установку `platforms;android-37.0` и `build-tools;37.0.0`, с явной проверкой наличия `android.jar` и `zipalign` перед Gradle-сборкой. `targetSdk` остаётся 36.

