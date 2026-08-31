package app.omnireader.android

import android.app.Application
import app.omnireader.android.core.cache.SafFileCache
import app.omnireader.android.data.db.AppDatabase
import app.omnireader.android.data.repository.LibraryRepository
import app.omnireader.android.metadata.MetadataExtractor
import app.omnireader.android.reader.ReaderRegistry
import app.omnireader.android.scanner.LibraryScanner

class OmniReaderApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val database = AppDatabase.create(app)
    val repository = LibraryRepository(database.libraryDao(), database.sourceFolderDao())
    val fileCache = SafFileCache(app)
    val metadataExtractor = MetadataExtractor(app, fileCache)
    val scanner = LibraryScanner(app, repository, metadataExtractor)
    val readerRegistry = ReaderRegistry(app, repository, fileCache)
}
