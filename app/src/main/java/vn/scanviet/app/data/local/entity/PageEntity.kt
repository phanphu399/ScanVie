package vn.scanviet.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Một trang của tài liệu. [imageFile] là đường dẫn tương đối trong vùng riêng của app.
 * Xoá tài liệu sẽ tự động xoá các trang nhờ khoá ngoại CASCADE.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId"])],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imageFile: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
)
