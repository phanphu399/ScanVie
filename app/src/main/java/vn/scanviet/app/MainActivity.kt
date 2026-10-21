package vn.scanviet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import vn.scanviet.app.navigation.ScanVietNavHost
import vn.scanviet.app.ui.root.MainViewModel
import vn.scanviet.app.ui.theme.ScanVietTheme
import vn.scanviet.app.ui.update.UpdateViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val theme by mainViewModel.theme.collectAsStateWithLifecycle()

            // `this` là Activity, nên ViewModel gắn với vòng đời Activity và dùng chung cho mọi
            // màn hình: Thư viện và Cài đặt cùng đọc một trạng thái cập nhật, và tiến trình tải
            // không bị hủy khi người dùng chuyển màn hình.
            val updateViewModel: UpdateViewModel = hiltViewModel(this)

            // Tự kiểm tra cập nhật mỗi khi ứng dụng vào foreground (bên trong tự giới hạn 24
            // giờ). Đặt ở đây chứ không ở màn hình Cài đặt: người dùng bình thường không mở
            // Cài đặt, nên trước đây họ không bao giờ nhận được thông báo cập nhật.
            //
            // `collect` thay vì `collectLatest`: đây là một tác vụ bắn-rồi-quên, không cần hủy.
            LaunchedEffect(Unit) {
                mainViewModel.foregroundSignals(this@MainActivity)
                    .filter { isStarted -> isStarted }
                    .collect { updateViewModel.autoCheckIfDue() }
            }

            ScanVietTheme(themePreference = theme) {
                ScanVietNavHost(updateViewModel = updateViewModel)
            }
        }
    }
}
