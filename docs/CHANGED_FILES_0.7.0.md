# Изменённые файлы 0.7.0

Относительно входной версии 0.6.0. Полные пути указаны от корня проекта.

## Новые

- `docs/LOCAL_VALIDATION_0.7.0.txt`

- `app/src/androidTest/java/com/sergey/reader/document/DocumentRoomTest.kt`
- `app/src/androidTest/java/com/sergey/reader/document/PdfBackendTest.kt`
- `app/src/main/java/com/sergey/reader/data/db/DocumentEntities.kt`
- `app/src/main/java/com/sergey/reader/document/DjvuDocumentBackend.kt`
- `app/src/main/java/com/sergey/reader/document/DocumentBackend.kt`
- `app/src/main/java/com/sergey/reader/document/DocumentGeometry.kt`
- `app/src/main/java/com/sergey/reader/document/DocumentPersistence.kt`
- `app/src/main/java/com/sergey/reader/document/DocumentTextLayer.kt`
- `app/src/main/java/com/sergey/reader/document/DocumentTiles.kt`
- `app/src/main/java/com/sergey/reader/document/PdfDocumentBackend.kt`
- `app/src/main/java/com/sergey/reader/ui/ReadingDestination.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentFullscreen.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentSearchSheet.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentSettings.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentSurfaceView.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentTileRenderer.kt`
- `app/src/main/java/com/sergey/reader/ui/document/DocumentViewer.kt`
- `app/src/test/java/com/sergey/reader/document/DocumentGeometryTest.kt`
- `app/src/test/java/com/sergey/reader/document/DocumentTextTest.kt`
- `docs/DOCUMENT_QA.md`
- `docs/DOCUMENT_VIEWER.md`
- `licenses/PdfiumAndroidKt-2.0.3.txt`
- `tools/audit-native-apk.py`
- `tools/test-document-migration.py`

- `docs/CHANGED_FILES_0.7.0.md` (этот перечень)

## Изменённые

- `.github/workflows/android.yml`
- `BUILD_NOTE.md`
- `README.md`
- `THIRD_PARTY_NOTICES.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/sergey/reader/ReaderApplication.kt`
- `app/src/main/java/com/sergey/reader/data/db/Daos.kt`
- `app/src/main/java/com/sergey/reader/data/db/ReaderDatabase.kt`
- `app/src/main/java/com/sergey/reader/data/settings/ReaderSettingsRepository.kt`
- `app/src/main/java/com/sergey/reader/ui/AppViewModels.kt`
- `app/src/main/java/com/sergey/reader/ui/ReaderApp.kt`
- `app/src/main/java/com/sergey/reader/ui/screens/SettingsScreen.kt`
- `tools/verify-source.py`

Удалённых файлов нет. ReaderScreen и 10 файлов парсеров побайтово совпадают с входной версией; это проверка состава исходников, не доказательство отсутствия runtime-регрессий.
