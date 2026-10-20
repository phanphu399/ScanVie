package vn.scanviet.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vn.scanviet.app.data.local.entity.OcrLineEntity
import vn.scanviet.app.data.local.entity.OcrPageEntity

/**
 * Truy vấn chữ đã nhận dạng.
 *
 * Vì sao ghi theo kiểu "xoá rồi chèn" trong một transaction: nhận dạng lại một tài liệu
 * sinh ra số dòng khác lần trước. Chèn đè theo khoá sẽ để lại các dòng thừa của lần cũ,
 * tạo ra chữ "ma" không có thật trong tài liệu. Xoá sạch rồi ghi lại là cách duy nhất
 * đảm bảo dữ liệu khớp với lần nhận dạng mới nhất.
 */
@Dao
interface OcrDao {

    @Query("SELECT * FROM ocr_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePages(documentId: String): Flow<List<OcrPageEntity>>

    @Query("SELECT * FROM ocr_lines WHERE documentId = :documentId ORDER BY pageIndex ASC, lineIndex ASC")
    fun observeLines(documentId: String): Flow<List<OcrLineEntity>>

    /** Chỉ các dòng của một trang; dùng khi cần dựng lại lớp chữ cho một trang cụ thể. */
    @Query(
        """
        SELECT * FROM ocr_lines
        WHERE documentId = :documentId AND pageIndex = :pageIndex
        ORDER BY lineIndex ASC
        """,
    )
    suspend fun getLines(documentId: String, pageIndex: Int): List<OcrLineEntity>

    @Query("SELECT COUNT(*) FROM ocr_pages WHERE documentId = :documentId")
    suspend fun countRecognizedPages(documentId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<OcrPageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<OcrLineEntity>)

    @Query("DELETE FROM ocr_lines WHERE documentId = :documentId")
    suspend fun deleteLines(documentId: String)

    @Query("DELETE FROM ocr_pages WHERE documentId = :documentId")
    suspend fun deletePages(documentId: String)
}
