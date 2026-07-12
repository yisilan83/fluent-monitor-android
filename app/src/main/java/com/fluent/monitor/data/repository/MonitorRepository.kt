package com.fluent.monitor.data.repository

import com.fluent.monitor.data.remote.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ResidualPoint(
    val iteration: Int,
    val continuity: Double,
    val xVelocity: Double,
    val yVelocity: Double,
    val energy: Double
)

data class TemperaturePoint(
    val iteration: Int,
    val temperature: Double
)

@Singleton
class MonitorRepository @Inject constructor(
    private val webSocketManager: WebSocketManager
) {
    private val _residualData = MutableStateFlow<List<ResidualPoint>>(emptyList())
    val residualData: StateFlow<List<ResidualPoint>> = _residualData.asStateFlow()

    private val _temperatureData = MutableStateFlow<List<TemperaturePoint>>(emptyList())
    val temperatureData: StateFlow<List<TemperaturePoint>> = _temperatureData.asStateFlow()

    private val _currentIteration = MutableStateFlow(0)
    val currentIteration: StateFlow<Int> = _currentIteration.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var observeJob: Job? = null

    fun startObserving(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return
        _isRunning.value = true

        observeJob = scope.launch {
            webSocketManager.messages
                .throttleWindow(500L)
                .collect { batch ->
                    if (batch.isEmpty()) return@collect

                    val residuals = _residualData.value.toMutableList()
                    val temperatures = _temperatureData.value.toMutableList()

                    for (msg in batch) {
                        residuals.add(
                            ResidualPoint(
                                iteration = msg.iteration,
                                continuity = msg.residuals.continuity,
                                xVelocity = msg.residuals.xVelocity,
                                yVelocity = msg.residuals.yVelocity,
                                energy = msg.residuals.energy
                            )
                        )
                        temperatures.add(
                            TemperaturePoint(
                                iteration = msg.iteration,
                                temperature = msg.customMonitors.regionAvgTemp
                            )
                        )
                    }

                    // Keep last 2000 points to avoid memory issues
                    _residualData.value = residuals.takeLast(2000)
                    _temperatureData.value = temperatures.takeLast(2000)
                    _currentIteration.value = batch.last().iteration
                }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        _isRunning.value = false
    }

    fun clearData() {
        _residualData.value = emptyList()
        _temperatureData.value = emptyList()
        _currentIteration.value = 0
    }

    /**
     * Accumulates values into batches emitted at fixed intervals.
     * All values received within [windowMillis] are collected and emitted as a list.
     */
    private fun <T> Flow<T>.throttleWindow(windowMillis: Long): Flow<List<T>> = flow {
        coroutineScope {
            val channel = Channel<T>(Channel.UNLIMITED)
            val collector = launch {
                collect { channel.send(it) }
            }

            try {
                while (isActive) {
                    delay(windowMillis)
                    val batch = mutableListOf<T>()
                    while (true) {
                        val item = channel.poll() ?: break
                        batch.add(item)
                    }
                    if (batch.isNotEmpty()) emit(batch.toList())
                }
            } finally {
                collector.cancel()
            }
        }
    }
}
