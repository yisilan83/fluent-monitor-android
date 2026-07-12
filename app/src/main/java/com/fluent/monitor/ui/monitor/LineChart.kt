package com.fluent.monitor.ui.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluent.monitor.ui.theme.ChartColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class YAxisScale { LINEAR, LOGARITHMIC }

data class ChartSeries(
    val label: String,
    val points: List<Pair<Float, Float>>,
    val color: Color,
    val lineWidth: Float = 2f
)

@Composable
fun LineChart(
    series: List<ChartSeries>,
    yAxisScale: YAxisScale,
    yAxisLabel: String,
    xAxisLabel: String,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    val axisLabelArgb = ChartColors.axisLabel.toArgb()
    val gridLineColor = ChartColors.gridLine

    Column(modifier = modifier) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            series.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = s.color, radius = 5.dp.toPx())
                    }
                    Text(
                        text = s.label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            val paddingLeft = 64.dp.toPx()
            val paddingRight = 16.dp.toPx()
            val paddingTop = 16.dp.toPx()
            val paddingBottom = 48.dp.toPx()

            val chartLeft = paddingLeft
            val chartRight = size.width - paddingRight
            val chartTop = paddingTop
            val chartBottom = size.height - paddingBottom
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

            val allX = series.flatMap { it.points.map { p -> p.first } }
            val allY = series.flatMap { it.points.map { p -> p.second } }
            if (allX.isEmpty() || allY.isEmpty()) return@Canvas

            val xMin = allX.min()
            val xMax = allX.max().let { if (it == xMin) it + 1f else it }

            val yMinRaw = allY.min()
            val yMaxRaw = allY.max().let { if (it == yMinRaw) yMinRaw + 1f else it }

            val (yMin, yMax) = when (yAxisScale) {
                YAxisScale.LOGARITHMIC -> {
                    val minPositive = allY.filter { it > 0f }.minOrNull() ?: 1e-10f
                    val lo = floor(log10(minPositive.toDouble())).toFloat()
                    val hi = ceil(log10(yMaxRaw.toDouble())).toFloat()
                    lo to hi
                }
                YAxisScale.LINEAR -> {
                    val range = yMaxRaw - yMinRaw
                    yMinRaw - range * 0.05f to yMaxRaw + range * 0.05f
                }
            }

            // --- Grid lines & Y-axis labels ---
            val yTicks = when (yAxisScale) {
                YAxisScale.LOGARITHMIC -> {
                    ((yMin.toInt())..(yMax.toInt())).map { it.toFloat() }
                }
                YAxisScale.LINEAR -> {
                    val tickCount = 5
                    (0..tickCount).map { i ->
                        yMin + (yMax - yMin) * i / tickCount
                    }
                }
            }

            val textPaint = android.graphics.Paint().apply {
                color = axisLabelArgb
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }

            yTicks.forEach { yVal ->
                val yPixel = when (yAxisScale) {
                    YAxisScale.LOGARITHMIC -> {
                        val logVal = log10(maxOf(yVal, yMin).toDouble()).toFloat()
                        chartBottom - (logVal - yMin) / (yMax - yMin) * chartHeight
                    }
                    YAxisScale.LINEAR -> {
                        chartBottom - (yVal - yMin) / (yMax - yMin) * chartHeight
                    }
                }

                drawLine(
                    color = gridLineColor.copy(alpha = 0.3f),
                    start = Offset(chartLeft, yPixel),
                    end = Offset(chartRight, yPixel),
                    strokeWidth = 1f
                )

                val label = when (yAxisScale) {
                    YAxisScale.LOGARITHMIC -> "1e${yVal.toInt()}"
                    YAxisScale.LINEAR -> String.format("%.1f", yVal)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    chartLeft - 6.dp.toPx(),
                    yPixel + 4.dp.toPx(),
                    textPaint
                )
            }

            // --- X-axis labels ---
            val xTextPaint = android.graphics.Paint().apply {
                color = axisLabelArgb
                textSize = 10.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val xTickCount = 4
            (0..xTickCount).forEach { i ->
                val xVal = xMin + (xMax - xMin) * i / xTickCount
                val xPixel = chartLeft + (xVal - xMin) / (xMax - xMin) * chartWidth
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(xVal),
                    xPixel,
                    chartBottom + 14.dp.toPx(),
                    xTextPaint
                )
            }

            // --- Axis labels ---
            val axisLabelPaint = android.graphics.Paint().apply {
                color = axisLabelArgb
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.apply {
                save()
                rotate(-90f, 14.dp.toPx(), size.height / 2f)
                drawText(yAxisLabel, 14.dp.toPx(), size.height / 2f, axisLabelPaint)
                restore()

                drawText(xAxisLabel, size.width / 2f, chartBottom + 38.dp.toPx(), axisLabelPaint)
            }

            // --- Draw series lines ---
            series.forEach { s ->
                if (s.points.size < 2) return@forEach

                val path = Path()
                s.points.forEachIndexed { index, (x, y) ->
                    val xPixel = chartLeft + (x - xMin) / (xMax - xMin) * chartWidth
                    val yPixel = when (yAxisScale) {
                        YAxisScale.LOGARITHMIC -> {
                            val logY = log10(
                                maxOf(y, 10.0.pow(yMin.toDouble()).toFloat()).toDouble()
                            ).toFloat()
                            chartBottom - (logY - yMin) / (yMax - yMin) * chartHeight
                        }
                        YAxisScale.LINEAR -> {
                            chartBottom - (y - yMin) / (yMax - yMin) * chartHeight
                        }
                    }

                    if (index == 0) {
                        path.moveTo(xPixel, yPixel)
                    } else {
                        path.lineTo(xPixel, yPixel)
                    }
                }

                drawPath(
                    path = path,
                    color = s.color,
                    style = Stroke(
                        width = s.lineWidth.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // --- Axis frame ---
            drawLine(
                color = gridLineColor,
                start = Offset(chartLeft, chartTop),
                end = Offset(chartLeft, chartBottom),
                strokeWidth = 1.5f
            )
            drawLine(
                color = gridLineColor,
                start = Offset(chartLeft, chartBottom),
                end = Offset(chartRight, chartBottom),
                strokeWidth = 1.5f
            )
        }
    }
}
