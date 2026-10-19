package vn.scanviet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.data.local.entity.DocumentEntity
import vn.scanviet.app.data.local.entity.PageEntity

@Dao
interface DocumentDao {

    /**
     * [query] là từ khoá ĐÃ chuẩn hoá bỏ dấu và đã thoát ký tự đặc biệt của LIKE.
     * Chuỗi rỗng nghĩa là không lọc.
     */
    @Query(
        """
        SELECT * FROM documents
        WHERE deletedAt IS NULL
          AND (:query = '' OR titleSearch LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY updatedAt DESC
        """,
    )
    fun observeActive(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    fun observeById(documentId: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :documentId")
    suspend fun findById(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    suspend fun getActive(): List<DocumentEntity>

    @Query("SELECT id FROM documents")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM documents WHERE deletedAt IS NULL")
    fun observeActiveSizeBytes(): Flow<Long>

    @Query("SELECT * FROM documents WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun getTrashedBefore(cutoff: Long): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPages(pages: List<PageEntity>)

    @Query(
        """
        UPDATE documents
        SET title = :title, titleSearch = :titleSearch, updatedAt = :updatedAt
        WHERE id = :documentId
        """,
    )
    suspend fun updateTitle(
        documentId: String,
        title: String,
        titleSearch: String,
        updatedAt: Long,
    ): Int

    @Query("UPDATE documents SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :documentId")
    suspend fun updateDeletedAt(documentId: String, deletedAt: Long?, updatedAt: Long): Int

    @Query("DELETE FROM documents WHERE id = :documentId")
    suspend fun deleteById(documentId: String): Int

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePages(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPages(documentId: String): List<PageEntity>
}
