package vn.scanviet.app.core.time

import javax.inject.Inject

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}
