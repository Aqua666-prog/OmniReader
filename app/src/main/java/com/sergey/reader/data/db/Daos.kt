package com.sergey.reader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAt, addedAt) DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET progress=:progress, positionBlock=:positionBlock, positionOffset=:positionOffset, totalBlocks=:totalBlocks, lastOpenedAt=:lastOpenedAt WHERE id=:bookId")
    suspend fun updateProgress(bookId: Long, progress: Float, positionBlock: Int, positionOffset: Int, totalBlocks: Int, lastOpenedAt: Long)

    @Query("UPDATE books SET favorite=:value WHERE id=:bookId")
    suspend fun setFavorite(bookId: Long, value: Boolean)

    @Query("UPDATE books SET wantToRead=:value WHERE id=:bookId")
    suspend fun setWantToRead(bookId: Long, value: Boolean)

    @Query("UPDATE books SET finished=:value, progress=CASE WHEN :value THEN 1.0 ELSE progress END WHERE id=:bookId")
    suspend fun setFinished(bookId: Long, value: Boolean)

    @Query("DELETE FROM books WHERE id=:bookId")
    suspend fun deleteById(bookId: Long)
}

@Dao
interface ContentDao {
    @Query("SELECT * FROM chapters WHERE bookId=:bookId ORDER BY orderIndex")
    suspend fun chapters(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM paragraphs WHERE bookId=:bookId ORDER BY chapterIndex, paragraphIndex")
    suspend fun paragraphs(bookId: Long): List<ParagraphEntity>

    @Query("SELECT * FROM paragraphs WHERE bookId=:bookId AND text LIKE '%' || :query || '%' ORDER BY chapterIndex, paragraphIndex LIMIT :limit")
    suspend fun search(bookId: Long, query: String, limit: Int = 200): List<ParagraphEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParagraphs(paragraphs: List<ParagraphEntity>)

    @Query("DELETE FROM chapters WHERE bookId=:bookId")
    suspend fun deleteChapters(bookId: Long)

    @Query("DELETE FROM paragraphs WHERE bookId=:bookId")
    suspend fun deleteParagraphs(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId=:bookId ORDER BY blockIndex")
    fun observeForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE bookId=:bookId AND blockIndex=:blockIndex LIMIT 1")
    suspend fun atPosition(bookId: Long, blockIndex: Int): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE bookId=:bookId ORDER BY blockIndex, startOffset, createdAt")
    fun observeForBook(bookId: Long): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: AnnotationEntity): Long

    @Update
    suspend fun update(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface DictionaryDao {
    @Query("SELECT * FROM dictionary_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DictionaryEntryEntity>>

    @Query("SELECT * FROM dictionary_entries WHERE normalizedTerm=:normalized LIMIT 1")
    suspend fun findNormalized(normalized: String): DictionaryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DictionaryEntryEntity): Long

    @Update
    suspend fun update(entry: DictionaryEntryEntity)

    @Query("DELETE FROM dictionary_entries WHERE id=:id")
    suspend fun delete(id: Long)
}

@Dao
interface ReadingProfileDao {
    @Query("SELECT * FROM book_reading_profiles WHERE bookId=:bookId LIMIT 1")
    fun observeForBook(bookId: Long): Flow<BookReadingProfileEntity?>

    @Query("SELECT * FROM book_reading_profiles WHERE bookId=:bookId LIMIT 1")
    suspend fun getForBook(bookId: Long): BookReadingProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: BookReadingProfileEntity)

    @Query("DELETE FROM book_reading_profiles WHERE bookId=:bookId")
    suspend fun deleteForBook(bookId: Long)
}
