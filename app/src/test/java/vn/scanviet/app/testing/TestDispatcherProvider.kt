package vn.scanviet.app.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import vn.scanviet.app.core.DispatcherProvider

/**
 * Test không có main looper thật, nên mọi dispatcher đều trỏ về dispatcher của test.
 * Nhờ vậy code chạy tuần tự, không cần sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
