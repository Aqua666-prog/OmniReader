package app.omnireader.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SourceFolderEntity::class,
        LibraryItemEntity::class,
        BookmarkEntity::class,
        NoteEntity::class,
        CollectionEntity::class,
        CollectionItemCrossRef::class,
        TagEntity::class,
        ItemTagCrossRef::class,
        ReadingSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sourceFolderDao(): SourceFolderDao
    abstract fun libraryDao(): LibraryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun noteDao(): NoteDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "omnireader.db",
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}
