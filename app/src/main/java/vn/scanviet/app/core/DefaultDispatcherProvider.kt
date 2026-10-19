package vn.scanviet.app.core

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val io = Dispatchers.IO
    override val default = Dispatchers.Default
    override val main = Dispatchers.Main.immediate
}
