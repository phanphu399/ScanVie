package vn.scanviet.app.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.TitleValidationError
import vn.scanviet.app.testing.FakeDocumentRepository
import vn.scanviet.app.testing.testDocument

class RenameDocumentUseCaseTest {

    private val repository = FakeDocumentRepository()
    private val useCase = RenameDocumentUseCase(repository)

    @Test
    fun `đổi tên hợp lệ`() = runTest {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))

        useCase("doc-1", "Tên mới")

        assertEquals("Tên mới", repository.getDocument("doc-1")?.title)
    }

    @Test
    fun `cắt khoảng trắng khi đổi tên`() = runTest {
        repository.seed(testDocument(id = "doc-1"))

        useCase("doc-1", "   Hóa đơn tháng 10   ")

        assertEquals("Hóa đơn tháng 10", repository.getDocument("doc-1")?.title)
    }

    @Test
    fun `tên rỗng bị từ chối và không thay đổi dữ liệu`() = runTest {
        repository.seed(testDocument(id = "doc-1", title = "Tên cũ"))

        try {
            useCase("doc-1", "   ")
            fail("Đáng lẽ phải ném InvalidDocumentTitleException")
        } catch (expected: InvalidDocumentTitleException) {
            assertEquals(TitleValidationError.EMPTY, expected.error)
        }

        assertEquals("Tên cũ", repository.getDocument("doc-1")?.title)
    }
}
