package vn.scanviet.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.scanviet.app.core.DispatcherProvider
import vn.scanviet.app.data.maintenance.AppStartupTasks

/**
 * Không có nội dung tài liệu nào được ghi log ở bất kỳ đâu trong ứng dụng.
 */
@HiltAndroidApp
class ScanVietApplication : Application() {

    @Inject
    lateinit var startupTasks: AppStartupTasks

    @Inject
    lateinit var dispatchers: DispatcherProvider

    /** SupervisorJob: một việc nền lỗi không được huỷ các việc nền khác. */
    private val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.default)
    }

    override fun onCreate() {
        super.onCreate()
        // Dọn dẹp và kiểm tra tính nhất quán giữa database và file, không chặn mở ứng dụng.
        applicationScope.launch { startupTasks.run() }
    }
}
