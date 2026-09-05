package com.sergey.reader

import android.app.Application
import android.content.ComponentCallbacks2
import com.sergey.reader.document.TesseractOcrEngine
import com.sergey.reader.data.backup.BackupManager
import com.sergey.reader.data.db.ReaderDatabase
import com.sergey.reader.data.fonts.FontRepository
import com.sergey.reader.data.repository.BookRepository
import com.sergey.reader.data.settings.ReaderSettingsRepository

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BackupManager.applyPendingRestore(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) TesseractOcrEngine.trimMemory()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        TesseractOcrEngine.trimMemory()
    }

    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    val pendingOpenBook = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val database: ReaderDatabase = ReaderDatabase.get(app)
    val documents = com.sergey.reader.document.DocumentPersistence(database)
    val books: BookRepository = BookRepository(app, database)
    val settings: ReaderSettingsRepository = ReaderSettingsRepository(app)
    val fonts: FontRepository = FontRepository(app)
    val backup: BackupManager = BackupManager(app, database)
}
