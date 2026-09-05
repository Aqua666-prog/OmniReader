package com.sergey.reader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "document_positions", foreignKeys = [ForeignKey(entity = BookEntity::class,parentColumns = ["id"],childColumns = ["bookId"],onDelete = ForeignKey.CASCADE)], indices = [Index("bookId")])
data class DocumentPositionEntity(@PrimaryKey val bookId: Long, val pageIndex: Int, val centerX: Double, val centerY: Double, val zoom: Double, val updatedAt: Long)

@Entity(tableName = "document_marks", foreignKeys = [
    ForeignKey(entity = AnnotationEntity::class,parentColumns = ["id"],childColumns = ["annotationId"],onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = BookEntity::class,parentColumns = ["id"],childColumns = ["bookId"],onDelete = ForeignKey.CASCADE)
], indices = [Index("bookId"), Index(value = ["bookId","pageIndex"])])
data class DocumentMarkEntity(@PrimaryKey val annotationId: Long, val bookId: Long, val pageIndex: Int, val boundsJson: String, val color: Long)

@Dao
interface DocumentDao {
    @Query("SELECT * FROM document_positions WHERE bookId=:bookId")
    suspend fun position(bookId: Long): DocumentPositionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: DocumentPositionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMark(mark: DocumentMarkEntity)
    @Query("SELECT * FROM document_marks WHERE bookId=:bookId")
    fun marks(bookId: Long): Flow<List<DocumentMarkEntity>>
    // Fixed-layout imports have one synthetic chapter in the historical schema.
    // This adapter is used ONLY for legacy notes/TTS links, never for viewer progress.
    @Query("SELECT paragraphIndex + 1 FROM paragraphs WHERE bookId=:bookId AND resourcePath=:page AND kind IN ('PDF_PAGE','DJVU_PAGE') LIMIT 1")
    suspend fun legacyBlock(bookId: Long, page: String): Int?
    @Query("SELECT resourcePath FROM paragraphs WHERE bookId=:bookId AND paragraphIndex=:paragraph AND kind IN ('PDF_PAGE','PDF_TEXT','DJVU_PAGE','DJVU_TEXT') LIMIT 1")
    suspend fun pageForLegacyParagraph(bookId: Long, paragraph: Int): String?
}
