package com.bluewhisper.presentation.screens.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.bluetooth.BTEvent
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.domain.model.ActiveSession
import com.bluewhisper.domain.model.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed class ConnectionUiEvent {
    data class Connected(val session: ActiveSession) : ConnectionUiEvent()
    object Rejected : ConnectionUiEvent()
    object Timeout : ConnectionUiEvent()
    data class Error(val message: String) : ConnectionUiEvent()
}

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val nearbyManager: NearbyConnectionsManager
) : ViewModel() {

    val connectionState = nearbyManager.connectionState

    private val _events = MutableSharedFlow<ConnectionUiEvent>(
        replay = 0, extraBufferCapacity = 4
    )
    val events: SharedFlow<ConnectionUiEvent> = _events.asSharedFlow()

    private val _timeoutSecondsLeft = MutableStateFlow(30)
    val timeoutSecondsLeft: StateFlow<Int> = _timeoutSecondsLeft.asStateFlow()

    private var timeoutJob: Job? = null

    init {
        observeConnectionState()
        observeBTEvents()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> {
                        cancelTimeout()
                        _events.emit(ConnectionUiEvent.Connected(state.session))
                    }
                    is ConnectionState.Idle -> {
                        // Connection was rejected or disconnected
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun observeBTEvents() {
        viewModelScope.launch {
            nearbyManager.events.collect { event ->
                when (event) {
                    is BTEvent.ConnectionRejected -> {
                        cancelTimeout()
                        _events.emit(ConnectionUiEvent.Rejected)
                    }
                    is BTEvent.Error -> {
                        cancelTimeout()
                        _events.emit(ConnectionUiEvent.Error(event.message))
                    }
                    else -> Unit
                }
            }
        }
    }

    // ── Sender side ───────────────────────────────────────────────
    fun startSenderTimeout() {
        timeoutJob?.cancel()
        _timeoutSecondsLeft.value = 30
        timeoutJob = viewModelScope.launch {
            repeat(30) { tick ->
                delay(1000)
                val remaining = 29 - tick
                _timeoutSecondsLeft.value = remaining
                if (remaining == 0) {
                    cancelRequest()
                    _events.emit(ConnectionUiEvent.Timeout)
                }
            }
        }
    }

    fun cancelRequest() {
        cancelTimeout()
        val state = connectionState.value
        if (state is ConnectionState.Requesting) {
            nearbyManager.rejectConnection(state.targetEndpointId)
        }
    }

    // ── Receiver side ─────────────────────────────────────────────
    fun acceptConnection(endpointId: String) {
        cancelTimeout()
        nearbyManager.acceptConnection(endpointId)
    }

    fun declineConnection(endpointId: String) {
        cancelTimeout()
        nearbyManager.rejectConnection(endpointId)
    }

    fun startReceiverTimeout(endpointId: String) {
        timeoutJob?.cancel()
        _timeoutSecondsLeft.value = 30
        timeoutJob = viewModelScope.launch {
            repeat(30) { tick ->
                delay(1000)
                val remaining = 29 - tick
                _timeoutSecondsLeft.value = remaining
                if (remaining == 0) {
                    // Auto-decline after 30s
                    nearbyManager.rejectConnection(endpointId)
                    _events.emit(ConnectionUiEvent.Timeout)
                }
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelTimeout()
    }
}
