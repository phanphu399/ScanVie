package vn.scanviet.app.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.core.text.VnTextNormalizer
import vn.scanviet.app.core.time.TimeProvider
import vn.scanviet.app.domain.storage.DocumentFileStorage
import vn.scanviet.app.data.local.ScanVietDatabase
import vn.scanviet.app.data.local.dao.DocumentDao
import vn.scanviet.app.data.local.entity.DocumentEntity
import vn.scanviet.app.data.local.entity.PageEntity
import vn.scanviet.app.data.local.pageIdOf
import vn.scanviet.app.data.local.toDomain
import vn.scanviet.app.domain.DocumentNotFoundException
import vn.scanviet.app.domain.model.Document
import vn.scanviet.app.domain.model.DocumentPage
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.domain.model.OcrStatus
import vn.scanviet.app.domain.repository.DocumentRepository

@Singleton
class OfflineDocumentRepository @Inject constructor(
    private val database: ScanVietDatabase,
    private val documentDao: DocumentDao,
    private val fileStorage: DocumentFileStorage,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : DocumentRepository {

    override fun observeDocuments(query: String): Flow<List<Document>> =
        documentDao.observeActive(VnTextNormalizer.normalizeForQuery(query))
            .map { entities -> entities.map { it.toDomain() } }

    override fun observeTrashedDocuments(): Flow<List<Document>> =
        documentDao.observeTrashed().map { entities -> entities.map { it.toDomain() } }

    override fun observeDocument(documentId: String): Flow<Document?> =
        documentDao.observeById(documentId).map { it?.toDomain() }

    override fun observePages(documentId: String): Flow<List<DocumentPage>> =
        documentDao.observePages(documentId).map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveSizeBytes(): Flow<Long> = documentDao.observeActiveSizeBytes()

    override suspend fun getDocument(documentId: String): Document? = withContext(dispatchers.io) {
        documentDao.findById(documentId)?.toDomain()
    }

    override suspend fun getDocuments(): List<Document> = withContext(dispatchers.io) {
        documentDao.getActive().map { it.toDomain() }
    }

    override suspend fun getPages(documentId: String): List<DocumentPage> = withContext(dispatchers.io) {
        documentDao.getPages(documentId).map { it.toDomain() }
    }

    override suspend fun getAllDocumentIds(): Set<String> = withContext(dispatchers.io) {
        documentDao.getAllIds().toSet()
    }

    override suspend fun createDocument(
        documentId: String,
        title: String,
        pages: List<NewDocumentPage>,
    ): Document = withContext(dispatchers.io) {
        val now = timeProvider.now()
        val document = DocumentEntity(
            id = documentId,
            title = title,
            titleSearch = VnTextNormalizer.normalize(title),
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            pageCount = pages.size,
            ocrStatus = OcrStatus.NONE.name,
            sizeBytes = pages.sumOf { it.sizeBytes },
        )
        // pageIndex đánh số từ 1 cho khớp với tên file (page_0001.jpg) và cách hiển thị "Trang 1".
        val pageEntities = pages.mapIndexed { index, page ->
            val pageIndex = index + 1
            PageEntity(
                id = pageIdOf(documentId, pageIndex),
                documentId = documentId,
                pageIndex = pageIndex,
                imageFile = page.relativePath,
                width = page.width,
                height = page.height,
                rotationDegrees = page.rotationDegrees,
            )
        }
        // Một transaction: hoặc có đủ thông tin tài liệu + các trang, hoặc không ghi gì.
        database.withTransaction {
            documentDao.insertDocument(document)
            documentDao.insertPages(pageEntities)
        }
        document.toDomain()
    }

    override suspend fun renameDocument(documentId: String, newTitle: String) =
        withContext(dispatchers.io) {
            val affected = documentDao.updateTitle(
                documentId = documentId,
                title = newTitle,
                titleSearch = VnTextNormalizer.normalize(newTitle),
                updatedAt = timeProvider.now(),
            )
            if (affected == 0) throw DocumentNotFoundException(documentId)
        }

    override suspend fun moveToTrash(documentId: String) = withContext(dispatchers.io) {
        val now = timeProvider.now()
        val affected = documentDao.updateDeletedAt(documentId, deletedAt = now, updatedAt = now)
        if (affected == 0) throw DocumentNotFoundException(documentId)
    }

    override suspend fun restoreFromTrash(documentId: String) = withContext(dispatchers.io) {
        val affected = documentDao.updateDeletedAt(
            documentId,
            deletedAt = null,
            updatedAt = timeProvider.now(),
        )
        if (affected == 0) throw DocumentNotFoundException(documentId)
    }

    override suspend fun countMissingPageFiles(documentId: String): Int = withContext(dispatchers.io) {
        documentDao.getPages(documentId).count { page -> !fileStorage.exists(page.imageFile) }
    }

    override suspend fun deleteForever(documentId: String) {
        withContext(dispatchers.io) {
            val affected = documentDao.deleteById(documentId)
            if (affected == 0) throw DocumentNotFoundException(documentId)
            // Bản ghi là nguồn sự thật: xoá bản ghi xong mới xoá file. Nếu bước xoá file lỗi,
            // DocumentStorageMaintenance sẽ dọn thư mục mồ côi ở lần mở ứng dụng sau.
            runCatching { fileStorage.deleteDocumentDirectory(documentId) }
        }
    }

    override suspend fun purgeTrashOlderThan(cutoffMillis: Long): Int = withContext(dispatchers.io) {
        val expired = documentDao.getTrashedBefore(cutoffMillis)
        if (expired.isEmpty()) return@withContext 0
        database.withTransaction {
            expired.forEach { documentDao.deleteById(it.id) }
        }
        expired.forEach { expiredDocument ->
            runCatching { fileStorage.deleteDocumentDirectory(expiredDocument.id) }
        }
        expired.size
    }
}
