package vn.scanviet.app.data.maintenance

import javax.inject.Inject
import javax.inject.Singleton
import vn.scanviet.app.data.file.DocumentFileStorage

/**
 * Việc chạy một lần lúc mở ứng dụng.
 * Không được làm sập ứng dụng: mọi lỗi dọn dẹp đều bị bỏ qua một cách có chủ đích,
 * vì lần mở sau sẽ thử lại. Không ghi log nội dung tài liệu.
 */
@Singleton
class AppStartupTasks @Inject constructor(
    private val maintenance: DocumentStorageMaintenance,
    private val storage: DocumentFileStorage,
) {

    suspend fun run(): StorageReconciliation? {
        runCatching { storage.cleanScratchSpace() }
        return runCatching { maintenance.run() }.getOrNull()
    }
}
