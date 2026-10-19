package vn.scanviet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import vn.scanviet.app.navigation.ScanVietNavHost
import vn.scanviet.app.ui.root.MainViewModel
import vn.scanviet.app.ui.theme.ScanVietTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val theme by mainViewModel.theme.collectAsStateWithLifecycle()

            ScanVietTheme(themePreference = theme) {
                ScanVietNavHost()
            }
        }
    }
}
