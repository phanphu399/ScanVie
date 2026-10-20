package vn.scanviet.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.scanviet.app.data.local.entity.OcrLineEntity
import vn.scanviet.app.data.local.entity.OcrPageEntity

/**
 * Kiểm tra migration v1 -> v2.
 *
 * Vì sao test này quan trọng hơn một test thường: migration sai sẽ XOÁ SẠCH danh sách tài
 * liệu của người dùng khi họ cập nhật ứng dụng — trong khi file ảnh vẫn còn trên máy. Đây là
 * kiểu lỗi không thể phát hiện bằng mắt khi phát triển, vì máy phát triển luôn cài mới.
 *
 * Vì sao tự dựng database v1 bằng SQL thay vì dùng `MigrationTestHelper`: thêm
 * `androidx.room:room-testing` chỉ để chạy một test là một dependency không tương xứng. Schema
 * v1 đã được Room xuất ra `app/schemas/.../1.json` (kể cả identity hash), nên dựng lại nó
 * bằng đúng câu SQL đó là cách kiểm tra trung thực và không phụ thuộc gì thêm.
 *
 * Ba điều được xác nhận:
 * 1. Tài liệu và trang của người dùng còn nguyên sau khi migration.
 * 2. Hai bảng mới tồn tại và dùng được.
 * 3. Xoá một tài liệu sẽ xoá luôn chữ nhận dạng của nó (khoá ngoại CASCADE) — không để rác.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScanVietDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var databaseFile: java.io.File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath(DB_NAME)
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
        createVersion1Database()
    }

    @After
    fun tearDown() {
        databaseFile.delete()
    }

    @Test
    fun `migration v1 sang v2 giữ nguyên tài liệu và trang của người dùng`() {
        openMigratedDatabase().use { database ->
            val documents = database.query("SELECT id, title FROM documents", emptyArray())
            assertTrue("Tài liệu của người dùng phải còn sau migration", documents.moveToFirst())
            assertEquals("doc-cu", documents.getString(0))
            assertEquals("Hóa đơn điện", documents.getString(1))
            assertEquals(1, documents.count)
            documents.close()

            val pages = database.query("SELECT id, imageFile FROM pages", emptyArray())
            assertTrue(pages.moveToFirst())
            assertEquals("doc-cu_p1", pages.getString(0))
            assertEquals("documents/doc-cu/page_0001.jpg", pages.getString(1))
            pages.close()
        }
    }

    @Test
    fun `hai bảng chữ nhận dạng tồn tại và ghi được sau migration`() = runBlocking {
        val database = Room.databaseBuilder(context, ScanVietDatabase::class.java, DB_NAME)
            .addMigrations(ScanVietDatabase.MIGRATION_1_2)
            .build()
        try {
            // Nếu migration quên một bảng hoặc một cột, lời gọi này sẽ ném lỗi.
            database.ocrDao().insertPages(
                listOf(
                    OcrPageEntity(
                        documentId = "doc-cu",
                        pageIndex = 1,
                        imageWidth = 1240,
                        imageHeight = 1754,
                    ),
                ),
            )
            database.ocrDao().insertLines(
                listOf(
                    OcrLineEntity(
                        documentId = "doc-cu",
                        pageIndex = 1,
                        lineIndex = 0,
                        text = "Hóa đơn điện",
                        left = 0.1f,
                        top = 0.2f,
                        right = 0.6f,
                        bottom = 0.25f,
                    ),
                ),
            )

            assertEquals(1, database.ocrDao().countRecognizedPages("doc-cu"))
            assertEquals(1, database.ocrDao().getLines("doc-cu", 1).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun `xoá tài liệu thì chữ nhận dạng của nó cũng bị xoá theo`() = runBlocking {
        val database = Room.databaseBuilder(context, ScanVietDatabase::class.java, DB_NAME)
            .addMigrations(ScanVietDatabase.MIGRATION_1_2)
            .build()
        try {
            database.ocrDao().insertPages(
                listOf(
                    OcrPageEntity(
                        documentId = "doc-cu",
                        pageIndex = 1,
                        imageWidth = 10,
                        imageHeight = 10,
                    ),
                ),
            )

            database.documentDao().deleteById("doc-cu")

            // Khoá ngoại CASCADE phải được bật; nếu không, chữ của tài liệu đã xoá sẽ nằm lại
            // mãi trong database và không ai dọn.
            assertEquals(0, database.ocrDao().countRecognizedPages("doc-cu"))
        } finally {
            database.close()
        }
    }

    private fun openMigratedDatabase(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(DB_VERSION_2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        if (oldVersion < 2) ScanVietDatabase.MIGRATION_1_2.migrate(db)
                    }
                },
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    /**
     * Dựng đúng database v1 như Room đã xuất ra `schemas/.../1.json`, gồm cả dòng
     * `room_master_table` mang identity hash của v1. Thiếu dòng đó, Room coi như database
     * không phải do nó tạo và sẽ từ chối migration.
     */
    private fun createVersion1Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(DB_VERSION_1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `documents` (" +
                                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                                "`titleSearch` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                                "`updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                                "`pageCount` INTEGER NOT NULL, `ocrStatus` TEXT NOT NULL, " +
                                "`sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_documents_deletedAt` " +
                                "ON `documents` (`deletedAt`)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_documents_updatedAt` " +
                                "ON `documents` (`updatedAt`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `pages` (" +
                                "`id` TEXT NOT NULL, `documentId` TEXT NOT NULL, " +
                                "`pageIndex` INTEGER NOT NULL, `imageFile` TEXT NOT NULL, " +
                                "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, " +
                                "`rotationDegrees` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                                "ON UPDATE NO ACTION ON DELETE CASCADE )",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_pages_documentId` " +
                                "ON `pages` (`documentId`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table " +
                                "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                                "VALUES(42, '$V1_IDENTITY_HASH')",
                        )

                        // Một tài liệu và một trang có thật, để chứng minh migration không xoá dữ liệu.
                        db.execSQL(
                            "INSERT INTO documents " +
                                "(id, title, titleSearch, createdAt, updatedAt, deletedAt, " +
                                "pageCount, ocrStatus, sizeBytes) " +
                                "VALUES ('doc-cu', 'Hóa đơn điện', 'hoa don dien', 1000, 2000, " +
                                "NULL, 1, 'NONE', 12345)",
                        )
                        db.execSQL(
                            "INSERT INTO pages " +
                                "(id, documentId, pageIndex, imageFile, width, height, rotationDegrees) " +
                                "VALUES ('doc-cu_p1', 'doc-cu', 1, " +
                                "'documents/doc-cu/page_0001.jpg', 1240, 1754, 0)",
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build()
        // Mở rồi đóng ngay để file database v1 được ghi xuống đĩa.
        FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase.apply {
            assertNotNull(this)
            close()
        }
    }

    private companion object {
        const val DB_NAME = ScanVietDatabase.NAME
        const val DB_VERSION_1 = 1
        const val DB_VERSION_2 = 2

        /** Identity hash của schema v1, lấy từ `app/schemas/.../1.json`. */
        const val V1_IDENTITY_HASH = "7e99dd608e0dad20767a4f90d2ffdbbb"
    }
}
