package vn.scanviet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Một dòng chữ nhận dạng được. Khoá chính là (documentId, pageIndex, lineIndex).
 *
 * Vì sao mỗi dòng một bản ghi thay vì một khối JSON trong một cột: hình bao của từng dòng
 * là dữ liệu có cấu trúc, cần đọc lại toàn bộ khi vẽ lớp chữ trong PDF. Nhồi JSON vào một
 * cột sẽ biến dữ liệu có cấu trúc thành chuỗi mờ, không truy vấn được và dễ hỏng khi đổi
 * định dạng. Tách bảng là cách lành mạnh hơn cho dữ liệu cỡ vài chục dòng mỗi trang.
 *
 * Toạ độ theo tỉ lệ 0..1 so với ảnh gốc của trang (xem [OcrPageEntity]).
 */
@Entity(
    tableName = "ocr_lines",
    primaryKeys = ["documentId", "pageIndex", "lineIndex"],
    foreignKeys = [
        ForeignKey(
            entity = OcrPageEntity::class,
            parentColumns = ["documentId", "pageIndex"],
            childColumns = ["documentId", "pageIndex"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class OcrLineEntity(
    val documentId: String,
    val pageIndex: Int,
    val lineIndex: Int,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)
