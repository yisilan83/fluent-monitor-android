package com.fluent.monitor.ui.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluent.monitor.data.remote.ConnectionState
import com.fluent.monitor.data.remote.WebSocketManager
import com.fluent.monitor.data.repository.MonitorRepository
import com.fluent.monitor.data.repository.ResidualPoint
import com.fluent.monitor.data.repository.TemperaturePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MonitorUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val currentIteration: Int = 0,
    val residualData: List<ResidualPoint> = emptyList(),
    val temperatureData: List<TemperaturePoint> = emptyList(),
    val serverUrl: String = DEFAULT_URL,
    val isMonitoring: Boolean = false
)

private const val DEFAULT_URL = "ws://10.0.2.2:8000/ws"

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val monitorRepository: MonitorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    init {
        // Observe connection state
        viewModelScope.launch {
            webSocketManager.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
            }
        }

        // Observe data from repository
        viewModelScope.launch {
            combine(
                monitorRepository.residualData,
                monitorRepository.temperatureData,
                monitorRepository.currentIteration
            ) { residuals, temps, iteration ->
                _uiState.value = _uiState.value.copy(
                    residualData = residuals,
                    temperatureData = temps,
                    currentIteration = iteration
                )
            }
        }
    }

    fun onServerUrlChange(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun toggleConnection() {
        val current = _uiState.value
        if (current.isMonitoring) {
            disconnect()
        } else {
            connect()
        }
    }

    private fun connect() {
        val url = _uiState.value.serverUrl.ifBlank { DEFAULT_URL }
        monitorRepository.clearData()
        webSocketManager.connect(url)
        monitorRepository.startObserving(viewModelScope)
        _uiState.value = _uiState.value.copy(isMonitoring = true)
    }

    private fun disconnect() {
        monitorRepository.stopObserving()
        webSocketManager.disconnect()
        _uiState.value = _uiState.value.copy(isMonitoring = false)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
