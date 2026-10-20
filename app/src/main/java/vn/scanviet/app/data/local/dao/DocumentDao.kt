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

    /**
     * Cập nhật trạng thái nhận dạng chữ.
     *
     * Không đụng tới `updatedAt`: nhận dạng chữ không phải là thay đổi nội dung do người
     * dùng thực hiện, nên không được đẩy tài liệu lên đầu danh sách "mới cập nhật".
     */
    @Query("UPDATE documents SET ocrStatus = :status WHERE id = :documentId")
    suspend fun updateOcrStatus(documentId: String, status: String): Int

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePages(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPages(documentId: String): List<PageEntity>

    /**
     * Trang có `pageIndex` nhỏ nhất của mỗi tài liệu trong danh sách.
     *
     * `GROUP BY documentId` + `MIN(pageIndex)` là cách SQLite chọn đúng một dòng mỗi nhóm;
     * lấy cả bảng rồi lọc trong Kotlin sẽ nạp thừa toàn bộ ảnh của mọi trang.
     */
    @Query(
        """
        SELECT * FROM pages
        WHERE pageIndex = (
            SELECT MIN(pageIndex) FROM pages AS inner_pages
            WHERE inner_pages.documentId = pages.documentId
        )
          AND documentId IN (:documentIds)
        """,
    )
    suspend fun getFirstPages(documentIds: List<String>): List<PageEntity>
}
