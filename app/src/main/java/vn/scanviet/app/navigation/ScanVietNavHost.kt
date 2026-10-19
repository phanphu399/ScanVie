package vn.scanviet.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import vn.scanviet.app.ui.library.DocumentDetailScreen
import vn.scanviet.app.ui.library.LibraryScreen
import vn.scanviet.app.ui.library.TrashScreen
import vn.scanviet.app.ui.scan.ScanPlaceholderScreen
import vn.scanviet.app.ui.settings.SettingsScreen

/**
 * Nơi duy nhất biết về tất cả màn hình.
 * Các màn hình chỉ nhận callback (onOpenDocument, onScanClick, ...) nên không phụ thuộc lẫn nhau:
 * Giai đoạn 2/3 chỉ cần đổi [ScanVietRoutes.SCAN] sang màn hình quét thật.
 */
@Composable
fun ScanVietNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ScanVietRoutes.LIBRARY,
    ) {
        composable(ScanVietRoutes.LIBRARY) {
            LibraryScreen(
                onScanClick = { navController.navigate(ScanVietRoutes.SCAN) },
                onOpenDocument = { documentId ->
                    navController.navigate(ScanVietRoutes.documentDetail(documentId))
                },
                onOpenTrash = { navController.navigate(ScanVietRoutes.TRASH) },
                onOpenSettings = { navController.navigate(ScanVietRoutes.SETTINGS) },
            )
        }

        composable(ScanVietRoutes.SCAN) {
            ScanPlaceholderScreen(onBack = { navController.popBackStack() })
        }

        composable(ScanVietRoutes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }

        composable(ScanVietRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTrash = { navController.navigate(ScanVietRoutes.TRASH) },
            )
        }

        composable(
            route = ScanVietRoutes.DOCUMENT_DETAIL,
            arguments = listOf(
                navArgument(ScanVietRoutes.ARG_DOCUMENT_ID) { type = NavType.StringType },
            ),
        ) {
            DocumentDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
