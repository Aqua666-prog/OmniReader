package com.sergey.reader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        ParagraphEntity::class,
        BookmarkEntity::class,
        AnnotationEntity::class,
        DictionaryEntryEntity::class,
        BookReadingProfileEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun contentDao(): ContentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun readingProfileDao(): ReadingProfileDao

    companion object {
        @Volatile private var instance: ReaderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS annotations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        blockIndex INTEGER NOT NULL,
                        startOffset INTEGER NOT NULL,
                        endOffset INTEGER NOT NULL,
                        selectedText TEXT NOT NULL,
                        type TEXT NOT NULL,
                        colorHex INTEGER NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId ON annotations(bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_annotations_bookId_blockIndex ON annotations(bookId, blockIndex)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dictionary_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        term TEXT NOT NULL,
                        normalizedTerm TEXT NOT NULL,
                        definition TEXT,
                        translation TEXT,
                        contextText TEXT,
                        bookId INTEGER,
                        blockIndex INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_normalizedTerm ON dictionary_entries(normalizedTerm)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_entries_bookId ON dictionary_entries(bookId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paragraphs ADD COLUMN kind TEXT NOT NULL DEFAULT 'PARAGRAPH'")
                db.execSQL("ALTER TABLE paragraphs ADD COLUMN resourcePath TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS book_reading_profiles (
                        bookId INTEGER NOT NULL PRIMARY KEY,
                        fontSizeSp REAL NOT NULL,
                        lineHeight REAL NOT NULL,
                        horizontalPaddingDp REAL NOT NULL,
                        theme TEXT NOT NULL,
                        justify INTEGER NOT NULL,
                        fontPath TEXT,
                        readerMode TEXT NOT NULL DEFAULT 'VERTICAL',
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_reading_profiles_bookId ON book_reading_profiles(bookId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN positionOffset INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): ReaderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }

        fun closeForRestore() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
