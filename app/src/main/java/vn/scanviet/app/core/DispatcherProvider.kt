package vn.scanviet.app.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Cho phép thay thế dispatcher trong unit test (test không có main looper thật).
 * Không dùng trực tiếp Dispatchers.IO rải rác trong code nghiệp vụ.
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
