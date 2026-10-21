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
import vn.scanviet.app.ui.scan.ScanScreen
import vn.scanviet.app.ui.settings.SettingsScreen
import vn.scanviet.app.ui.update.UpdateViewModel
import vn.scanviet.app.ui.viewer.DocumentViewerScreen

/**
 * Nơi duy nhất biết về tất cả màn hình.
 * Các màn hình chỉ nhận callback (onOpenDocument, onScanClick, ...) nên không phụ thuộc lẫn nhau.
 *
 * [updateViewModel] được truyền vào chứ không tự tạo ở từng màn hình: nó thuộc phạm vi Activity
 * (xem `MainActivity`) để trạng thái cập nhật không bị mất khi người dùng chuyển màn hình, và
 * để băng nhắc ở Thư viện nói cùng một chuyện với khối trong Cài đặt.
 */
@Composable
fun ScanVietNavHost(
    updateViewModel: UpdateViewModel,
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
                updateViewModel = updateViewModel,
            )
        }

        composable(ScanVietRoutes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onDocumentSaved = { documentId ->
                    // Thay màn hình quét bằng màn hình chi tiết: sau khi lưu, bấm "Quay lại"
                    // sẽ về danh sách tài liệu chứ không quay lại danh sách trang đã lưu.
                    navController.navigate(ScanVietRoutes.documentDetail(documentId)) {
                        popUpTo(ScanVietRoutes.SCAN) { inclusive = true }
                    }
                },
            )
        }

        composable(ScanVietRoutes.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }

        composable(ScanVietRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenTrash = { navController.navigate(ScanVietRoutes.TRASH) },
                updateViewModel = updateViewModel,
            )
        }

        composable(
            route = ScanVietRoutes.DOCUMENT_DETAIL,
            arguments = listOf(
                navArgument(ScanVietRoutes.ARG_DOCUMENT_ID) { type = NavType.StringType },
            ),
        ) {
            DocumentDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenPage = { pageIndex ->
                    val documentId = navController.currentBackStackEntry
                        // Tham số nằm ở chính màn hình chi tiết đang đứng trên đỉnh stack.
                        ?.arguments?.getString(ScanVietRoutes.ARG_DOCUMENT_ID)
                    if (documentId != null) {
                        navController.navigate(
                            ScanVietRoutes.documentViewer(documentId, pageIndex),
                        )
                    }
                },
            )
        }

        composable(
            route = ScanVietRoutes.DOCUMENT_VIEWER,
            arguments = listOf(
                navArgument(ScanVietRoutes.ARG_DOCUMENT_ID) { type = NavType.StringType },
                navArgument(ScanVietRoutes.ARG_PAGE_INDEX) {
                    type = NavType.IntType
                    defaultValue = 1
                },
            ),
        ) {
            DocumentViewerScreen(onBack = { navController.popBackStack() })
        }
    }
}
