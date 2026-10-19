package vn.scanviet.app.data.maintenance

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import vn.scanviet.app.core.time.TimeProvider
import vn.scanviet.app.data.file.DocumentFileStorage
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.repository.DocumentRepository

/**
 * Kết quả dọn dẹp lúc mở ứng dụng.
 * Không chứa tên tài liệu hay nội dung tài liệu nên có thể hiển thị/log an toàn.
 */
data class StorageReconciliation(
    val purgedDocuments: Int,
    val orphanDirectoriesDeleted: Int,
    val documentsMovedToTrash: Int,
)

/**
 * Giữ cho database và file trên máy luôn khớp nhau.
 *
 * Ba việc, theo đúng thứ tự:
 * 1. Xoá vĩnh viễn tài liệu trong thùng rác đã quá hạn giữ.
 * 2. Xoá thư mục file không còn bản ghi nào trỏ tới (rác sau khi khôi phục/lỗi).
 * 3. Tài liệu còn bản ghi nhưng mất toàn bộ file ảnh -> chuyển vào thùng rác
 *    (không xoá thẳng, để người dùng vẫn thấy và quyết định).
 */
@Singleton
class DocumentStorageMaintenance @Inject constructor(
    private val repository: DocumentRepository,
    private val storage: DocumentFileStorage,
    private val timeProvider: TimeProvider,
) {

    suspend fun run(): StorageReconciliation {
        val retentionMillis = TimeUnit.DAYS.toMillis(DocumentRules.TRASH_RETENTION_DAYS.toLong())
        val purged = repository.purgeTrashOlderThan(timeProvider.now() - retentionMillis)

        val knownIds = repository.getAllDocumentIds()
        val orphanDirectories = storage.listDocumentDirectories().filterNot { it in knownIds }
        orphanDirectories.forEach { storage.deleteDocumentDirectory(it) }

        var movedToTrash = 0
        repository.getDocuments().forEach { document ->
            val pages = repository.getPages(document.id)
            val allFilesMissing = pages.isNotEmpty() && pages.all { !storage.exists(it.imageFile) }
            if (allFilesMissing) {
                repository.moveToTrash(document.id)
                movedToTrash++
            }
        }

        return StorageReconciliation(
            purgedDocuments = purged,
            orphanDirectoriesDeleted = orphanDirectories.size,
            documentsMovedToTrash = movedToTrash,
        )
    }
}
