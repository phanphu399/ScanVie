package vn.scanviet.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import vn.scanviet.app.data.dev.SampleDocumentFactory
import vn.scanviet.app.ui.common.UiError
import vn.scanviet.app.ui.common.toUiError

/**
 * Trạng thái của công cụ thử nghiệm tạo tài liệu mẫu.
 * Công cụ này chỉ được gọi khi [vn.scanviet.app.BuildConfig.DEBUG] = true.
 */
sealed interface SampleState {

    data object Idle : SampleState

    data object Running : SampleState

    data object Done : SampleState

    data class Failed(val error: UiError) : SampleState
}

@HiltViewModel
class SampleDataViewModel @Inject constructor(
    private val sampleDocumentFactory: SampleDocumentFactory,
) : ViewModel() {

    private val _state = MutableStateFlow<SampleState>(SampleState.Idle)
    val state: StateFlow<SampleState> = _state

    fun onCreateSampleDocument() {
        if (_state.value == SampleState.Running) return
        _state.value = SampleState.Running
        viewModelScope.launch {
            _state.value = runCatching { sampleDocumentFactory.createTwoPageDocument() }
                .fold(
                    onSuccess = { SampleState.Done },
                    onFailure = { failure -> SampleState.Failed(failure.toUiError()) },
                )
        }
    }

    fun onResultShown() {
        _state.value = SampleState.Idle
    }
}
