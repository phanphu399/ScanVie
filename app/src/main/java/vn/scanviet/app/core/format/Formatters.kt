package vn.scanviet.app.core.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Định dạng hiển thị thuần Kotlin (không cần Android Context) để unit test được.
 * Dùng Long epoch millis thay cho java.time để tránh phải bật desugaring.
 */
object Formatters {

    private val VIETNAMESE = Locale.forLanguageTag("vi-VN")

    private const val KILOBYTE = 1024.0
    private const val MEGABYTE = KILOBYTE * 1024
    private const val GIGABYTE = MEGABYTE * 1024

    /** 1_208_000 -> "1,2 MB" */
    fun fileSize(bytes: Long): String {
        val value = bytes.toDouble()
        return when {
            value < KILOBYTE -> "$bytes B"
            value < MEGABYTE -> String.format(VIETNAMESE, "%.0f KB", value / KILOBYTE)
            value < GIGABYTE -> String.format(VIETNAMESE, "%.1f MB", value / MEGABYTE)
            else -> String.format(VIETNAMESE, "%.2f GB", value / GIGABYTE)
        }
    }

    /** 14/10/2026 09:30 */
    fun dateTime(epochMillis: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", VIETNAMESE).format(Date(epochMillis))

    /**
     * "Vừa xong", "12 phút trước", "3 giờ trước", "Hôm qua", sau đó là ngày cụ thể.
     * Thời gian được tính từ [now] để test được.
     */
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - epochMillis
        if (diff < 0) return "Vừa xong"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return when {
            minutes < 1 -> "Vừa xong"
            minutes < 60 -> "$minutes phút trước"
            hours < 24 -> "$hours giờ trước"
            days == 1L -> "Hôm qua"
            days < 7 -> "$days ngày trước"
            else -> SimpleDateFormat("dd/MM/yyyy", VIETNAMESE).format(Date(epochMillis))
        }
    }
}
