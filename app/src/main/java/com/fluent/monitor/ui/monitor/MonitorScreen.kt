package com.fluent.monitor.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluent.monitor.data.remote.ConnectionState
import com.fluent.monitor.ui.theme.ChartColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fluent Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Connection Card ---
            ConnectionCard(
                serverUrl = uiState.serverUrl,
                connectionState = uiState.connectionState,
                isMonitoring = uiState.isMonitoring,
                currentIteration = uiState.currentIteration,
                onUrlChange = viewModel::onServerUrlChange,
                onToggle = viewModel::toggleConnection
            )

            // --- Residual Chart ---
            if (uiState.residualData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        LineChart(
                            title = "迭代残差 (Residuals)",
                            series = listOf(
                                ChartSeries(
                                    label = "continuity",
                                    points = uiState.residualData.map {
                                        it.iteration.toFloat() to it.continuity.toFloat()
                                    },
                                    color = ChartColors.continuity
                                ),
                                ChartSeries(
                                    label = "x-velocity",
                                    points = uiState.residualData.map {
                                        it.iteration.toFloat() to it.xVelocity.toFloat()
                                    },
                                    color = ChartColors.xVelocity
                                ),
                                ChartSeries(
                                    label = "y-velocity",
                                    points = uiState.residualData.map {
                                        it.iteration.toFloat() to it.yVelocity.toFloat()
                                    },
                                    color = ChartColors.yVelocity
                                ),
                                ChartSeries(
                                    label = "energy",
                                    points = uiState.residualData.map {
                                        it.iteration.toFloat() to it.energy.toFloat()
                                    },
                                    color = ChartColors.energy
                                )
                            ),
                            yAxisScale = YAxisScale.LOGARITHMIC,
                            yAxisLabel = "Residual",
                            xAxisLabel = "Iteration",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- Temperature Chart ---
            if (uiState.temperatureData.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        LineChart(
                            title = "区域平均温度 (Region Avg Temp)",
                            series = listOf(
                                ChartSeries(
                                    label = "T_avg",
                                    points = uiState.temperatureData.map {
                                        it.iteration.toFloat() to it.temperature.toFloat()
                                    },
                                    color = ChartColors.temperature,
                                    lineWidth = 2.5f
                                )
                            ),
                            yAxisScale = YAxisScale.LINEAR,
                            yAxisLabel = "Temperature (K)",
                            xAxisLabel = "Iteration",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // --- Empty state ---
            if (uiState.residualData.isEmpty() && uiState.isMonitoring) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "等待数据...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    serverUrl: String,
    connectionState: ConnectionState,
    isMonitoring: Boolean,
    currentIteration: Int,
    onUrlChange: (String) -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "WebSocket 连接",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onUrlChange,
                label = { Text("服务器地址") },
                singleLine = true,
                enabled = !isMonitoring,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusColor, statusText) = when (connectionState) {
                        is ConnectionState.Connected -> Color(0xFF4CAF50) to "已连接"
                        is ConnectionState.Connecting -> Color(0xFFFFC107) to "连接中..."
                        is ConnectionState.Error -> Color(0xFFF44336) to "错误: ${connectionState.message}"
                        is ConnectionState.Disconnected -> Color(0xFF9E9E9E) to "未连接"
                    }
                    Surface(
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor,
                        content = {}
                    ) {
                        Box(modifier = Modifier.height(12.dp).width(12.dp))
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Iteration counter
                if (currentIteration > 0) {
                    Text(
                        text = "Iter: $currentIteration",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(if (isMonitoring) "断开连接" else "连接服务器")
            }
        }
    }
}
