package vn.scanviet.app.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vn.scanviet.app.domain.DocumentRules
import vn.scanviet.app.domain.EmptyDocumentException
import vn.scanviet.app.domain.InvalidDocumentTitleException
import vn.scanviet.app.domain.TitleValidationError
import vn.scanviet.app.domain.model.NewDocumentPage
import vn.scanviet.app.testing.FakeDocumentRepository

class CreateDocumentUseCaseTest {

    private val repository = FakeDocumentRepository()
    private val useCase = CreateDocumentUseCase(repository)

    private val onePage = listOf(
        NewDocumentPage(relativePath = "documents/doc-1/page_0001.jpg", width = 10, height = 20, sizeBytes = 100),
    )

    @Test
    fun `tạo tài liệu với tên hợp lệ`() = runTest {
        val document = useCase(documentId = "doc-1", title = "Hóa đơn điện", pages = onePage)

        assertEquals("doc-1", document.id)
        assertEquals("Hóa đơn điện", document.title)
        assertEquals(1, document.pageCount)
        assertEquals("Hóa đơn điện", repository.lastCreatedTitle)
    }

    @Test
    fun `cắt khoảng trắng ở hai đầu tên tài liệu`() = runTest {
        useCase(documentId = "doc-1", title = "  Biên lai  ", pages = onePage)

        assertEquals("Biên lai", repository.lastCreatedTitle)
    }

    @Test
    fun `tên rỗng bị từ chối`() = runTest {
        try {
            useCase(documentId = "doc-1", title = "   ", pages = onePage)
            fail("Đáng lẽ phải ném InvalidDocumentTitleException")
        } catch (expected: InvalidDocumentTitleException) {
            assertEquals(TitleValidationError.EMPTY, expected.error)
        }
    }

    @Test
    fun `tên quá dài bị từ chối`() = runTest {
        val longTitle = "a".repeat(DocumentRules.MAX_TITLE_LENGTH + 1)
        try {
            useCase(documentId = "doc-1", title = longTitle, pages = onePage)
            fail("Đáng lẽ phải ném InvalidDocumentTitleException")
        } catch (expected: InvalidDocumentTitleException) {
            assertEquals(TitleValidationError.TOO_LONG, expected.error)
        }
    }

    @Test
    fun `tên đúng độ dài tối đa được chấp nhận`() = runTest {
        val maxTitle = "a".repeat(DocumentRules.MAX_TITLE_LENGTH)
        val document = useCase(documentId = "doc-1", title = maxTitle, pages = onePage)

        assertEquals(DocumentRules.MAX_TITLE_LENGTH, document.title.length)
    }

    @Test
    fun `tài liệu không có trang nào bị từ chối`() = runTest {
        try {
            useCase(documentId = "doc-1", title = "Hợp đồng", pages = emptyList())
            fail("Đáng lẽ phải ném EmptyDocumentException")
        } catch (_: EmptyDocumentException) {
            // Đúng loại lỗi mong đợi.
        }
    }

    @Test
    fun `không ghi gì vào repository khi tên không hợp lệ`() = runTest {
        runCatching { useCase(documentId = "doc-1", title = "", pages = onePage) }

        assertTrue(repository.getDocuments().isEmpty())
    }
}
