package vn.scanviet.app.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra so sánh phiên bản.
 *
 * Bộ test này quan trọng hơn vẻ ngoài của nó: nếu so sánh sai, ứng dụng **im lặng** không bao
 * giờ báo cập nhật, và không có thông báo lỗi nào để lần ra. Trường hợp `0.10.0` so với `0.9.0`
 * là ví dụ điển hình — so chuỗi cho kết quả ngược lại hoàn toàn.
 */
class VersionComparatorTest {

    @Test
    fun `phien ban giong nhau tra ve 0`() {
        assertEquals(0, VersionComparator.compare("0.4.0", "0.4.0"))
    }

    @Test
    fun `so sanh theo so chu khong phai so chuoi`() {
        // Đây là lý do tồn tại của lớp này: so chuỗi sẽ nói "0.10.0" < "0.9.0".
        assertTrue(VersionComparator.isNewer("0.10.0", "0.9.0"))
        assertFalse(VersionComparator.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun `phien ban thieu phan duoc coi la 0`() {
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"))
        assertEquals(0, VersionComparator.compare("1", "1.0.0"))
    }

    @Test
    fun `major moi hon thi moi hon du minor nho hon`() {
        assertTrue(VersionComparator.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun `patch moi hon du major va minor bang nhau`() {
        assertTrue(VersionComparator.isNewer("1.2.4", "1.2.3"))
    }

    @Test
    fun `bo qua hau to beta khi so sanh`() {
        // "0.4.0-beta" được coi là 0.4.0, tức bằng bản 0.4.0 chính thức.
        assertEquals(0, VersionComparator.compare("0.4.0-beta", "0.4.0"))
        assertFalse(VersionComparator.isNewer("0.4.0-beta", "0.4.0"))
    }

    @Test
    fun `hau tu khong lam phien ban thanh moi hon`() {
        assertFalse(VersionComparator.isNewer("0.4.0-rc1", "0.4.0"))
    }

    @Test
    fun `chuoi rong duoc coi la phien ban 0`() {
        assertTrue(VersionComparator.isNewer("0.1.0", ""))
        assertFalse(VersionComparator.isNewer("", "0.1.0"))
        assertEquals(0, VersionComparator.compare("", ""))
    }

    @Test
    fun `chuoi khong co so duoc coi la phien ban 0`() {
        assertTrue(VersionComparator.isNewer("1.0.0", "khong-phai-phien-ban"))
        assertEquals(0, VersionComparator.compare("abc", "xyz"))
    }

    @Test
    fun `khoang trang thua khong anh huong`() {
        assertEquals(0, VersionComparator.compare(" 1.2.3 ", "1.2.3"))
    }

    @Test
    fun `phien ban tu GitHub co tien to v duoc xu ly truoc khi so sanh`() {
        // GitHubReleaseSource bỏ tiền tố "v" trước khi tạo AppRelease, nhưng comparator vẫn
        // phải đọc đúng phần số nếu tiền tố lọt vào từ nguồn khác. Đây không phải chi tiết vụn:
        // nếu coi "v0.5.0" là phiên bản 0, ứng dụng sẽ IM LẶNG không bao giờ báo cập nhật.
        assertTrue(VersionComparator.isNewer("v0.5.0", "0.4.0"))
        assertTrue(VersionComparator.isNewer("V0.5.0", "0.4.0"))
        assertEquals(0, VersionComparator.compare("v0.4.0", "0.4.0"))
        assertEquals(0, VersionComparator.compare("V0.4.0", "0.4.0"))
    }

    @Test
    fun `tien to v khong lam hong so sanh hai chu so`() {
        assertTrue(VersionComparator.isNewer("v0.10.0", "v0.9.0"))
    }

    @Test
    fun `phan khong phai so o giua bi cat bo`() {
        // "1.2.x" -> "1.2" ; so với "1.2.9" thì 1.2.9 mới hơn.
        assertTrue(VersionComparator.isNewer("1.2.9", "1.2.x"))
    }
}
