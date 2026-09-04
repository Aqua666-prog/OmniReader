package com.sergey.reader.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TtsUiState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val bookId: Long = 0L,
    val blockIndex: Int = 0,
    val title: String = "",
    val rangeStart: Int = -1,
    val rangeEnd: Int = -1,
    val sleepDeadlineMillis: Long = 0L,
    val error: String? = null
) {
    val hasRange: Boolean get() = rangeStart >= 0 && rangeEnd > rangeStart
    val sleepTimerActive: Boolean get() = sleepDeadlineMillis > System.currentTimeMillis()
}

object ReaderTtsController {
    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    internal fun publish(value: TtsUiState) {
        _state.value = value
    }
}
