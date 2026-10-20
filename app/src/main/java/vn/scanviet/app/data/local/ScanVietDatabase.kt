package vn.scanviet.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vn.scanviet.app.data.local.dao.DocumentDao
import vn.scanviet.app.data.local.dao.OcrDao
import vn.scanviet.app.data.local.entity.DocumentEntity
import vn.scanviet.app.data.local.entity.OcrLineEntity
import vn.scanviet.app.data.local.entity.OcrPageEntity
import vn.scanviet.app.data.local.entity.PageEntity

/**
 * Cơ sở dữ liệu cục bộ.
 *
 * QUY TẮC: không bao giờ dùng fallbackToDestructiveMigration() khi phát hành.
 * Nó sẽ xoá sạch thông tin quản lý tài liệu của người dùng (trong khi file vẫn còn),
 * khiến ứng dụng mất toàn bộ danh sách tài liệu sau khi cập nhật.
 * Mỗi lần đổi schema: tăng [version], viết Migration, và thêm test migration.
 *
 * Lịch sử phiên bản:
 * - v1: documents, pages.
 * - v2: thêm ocr_pages, ocr_lines (Giai đoạn 3 — nhận dạng chữ tiếng Việt).
 */
@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        OcrPageEntity::class,
        OcrLineEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ScanVietDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao

    abstract fun ocrDao(): OcrDao

    companion object {
        const val NAME = "scanviet.db"

        /**
         * v1 -> v2: thêm hai bảng chữ nhận dạng.
         *
         * Chỉ THÊM bảng, không sửa bảng cũ, nên dữ liệu tài liệu hiện có của người dùng
         * được giữ nguyên vẹn. Khoá ngoại CASCADE đảm bảo xoá tài liệu là xoá luôn chữ
         * của nó, không để lại rác.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_pages` (
                        `documentId` TEXT NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `imageWidth` INTEGER NOT NULL,
                        `imageHeight` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`, `pageIndex`),
                        FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ocr_pages_documentId` ON `ocr_pages` (`documentId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_lines` (
                        `documentId` TEXT NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `lineIndex` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `left` REAL NOT NULL,
                        `top` REAL NOT NULL,
                        `right` REAL NOT NULL,
                        `bottom` REAL NOT NULL,
                        PRIMARY KEY(`documentId`, `pageIndex`, `lineIndex`),
                        FOREIGN KEY(`documentId`, `pageIndex`)
                            REFERENCES `ocr_pages`(`documentId`, `pageIndex`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ocr_lines_documentId` ON `ocr_lines` (`documentId`)",
                )
            }
        }
    }
}
