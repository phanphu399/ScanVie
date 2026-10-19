package vn.scanviet.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import vn.scanviet.app.data.local.dao.DocumentDao
import vn.scanviet.app.data.local.entity.DocumentEntity
import vn.scanviet.app.data.local.entity.PageEntity

/**
 * Cơ sở dữ liệu cục bộ.
 *
 * QUY TẮC: không bao giờ dùng fallbackToDestructiveMigration() khi phát hành.
 * Nó sẽ xoá sạch thông tin quản lý tài liệu của người dùng (trong khi file vẫn còn),
 * khiến ứng dụng mất toàn bộ danh sách tài liệu sau khi cập nhật.
 * Mỗi lần đổi schema: tăng [version], viết Migration, và thêm test migration.
 */
@Database(
    entities = [DocumentEntity::class, PageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScanVietDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    companion object {
        const val NAME = "scanviet.db"
    }
}
