package com.sergey.reader

import android.app.Application
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

    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(app: Application) {
    val pendingOpenBook = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val database: ReaderDatabase = ReaderDatabase.get(app)
    val books: BookRepository = BookRepository(app, database)
    val settings: ReaderSettingsRepository = ReaderSettingsRepository(app)
    val fonts: FontRepository = FontRepository(app)
    val backup: BackupManager = BackupManager(app, database)
}
