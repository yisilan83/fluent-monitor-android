package com.fluent.monitor.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FluentMessage(
    val type: String,
    val iteration: Int,
    val residuals: Residuals,
    @Json(name = "custom_monitors") val customMonitors: CustomMonitors
)

@JsonClass(generateAdapter = true)
data class Residuals(
    val continuity: Double,
    @Json(name = "x-velocity") val xVelocity: Double,
    @Json(name = "y-velocity") val yVelocity: Double,
    val energy: Double
)

@JsonClass(generateAdapter = true)
data class CustomMonitors(
    @Json(name = "region_avg_temp") val regionAvgTemp: Double
)
