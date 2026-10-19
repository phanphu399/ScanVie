package vn.scanviet.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Tìm `Activity` đang chứa [this] context.
 *
 * Cần thiết vì trình quét tài liệu là một Activity của Play services: API của nó yêu cầu
 * một `Activity`, trong khi `LocalContext.current` chỉ đảm bảo là `Context`.
 * Trả `null` nếu không tìm thấy (trường hợp không xảy ra trong ứng dụng thật, nhưng
 * không được để crash nếu có).
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
