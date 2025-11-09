package com.example.sensorapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorReader(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    companion object {
        private const val TAG = "SensorReader"
        private val COLOR_SENSOR_TYPE: Int? = runCatching {
            Sensor::class.java.getField("TYPE_COLOR").getInt(null)
        }.getOrNull()
        private val NUMBER_LOCALE: Locale = Locale.US
    }

    private fun formatValue(value: Float, width: Int = 10, decimals: Int = 4): String {
        val pattern = "%${width}.${decimals}f"
        return String.format(NUMBER_LOCALE, pattern, value)
    }

    private fun formatValueWithUnit(value: Float, unit: String, unitWidth: Int = 10, valueWidth: Int = 10, decimals: Int = 2): String {
        val formattedValue = formatValue(value, width = valueWidth, decimals = decimals)
        val paddedUnit = unit.padEnd(unitWidth)
        return "${formattedValue} ${paddedUnit}"
    }

    private fun formatRawValues(values: FloatArray): String {
        if (values.isEmpty()) {
            return "-"
        }
        if (values.size == 1) {
            return formatValue(values.first(), width = 10, decimals = 4)
        }
        val indexWidth = maxOf(2, (values.size - 1).toString().length)
        val entriesPerLine = 2

        return values.mapIndexed { index, value ->
            val label = index.toString().padStart(indexWidth, ' ')
            val formatted = formatValue(value, width = 10, decimals = 4)
            "[${label}] ${formatted}"
        }.chunked(entriesPerLine).joinToString("\n") { chunk ->
            chunk.joinToString(separator = "    ")
        }
    }

    fun discoverSensors(): List<SensorInfo> {
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        Log.d(TAG, "Total sensors available: ${allSensors.size}")
        
        if (allSensors.isEmpty()) {
            Log.d(TAG, "No sensors available on this device")
            return emptyList()
        }

        val targetSensors = allSensors.mapNotNull { sensor ->
            val type = classifySensor(sensor)
            if (type == SensorType.UNKNOWN) {
                null
            } else {
                Log.d(TAG, "Discovered ${sensor.name} (${sensor.vendor}) - Android type: ${sensor.type}, Classified as: $type")
                val stringType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    sensor.stringType ?: "unknown"
                } else {
                    "unknown"
                }
                SensorInfo(
                    sensor = sensor,
                    type = type,
                    name = sensor.name,
                    vendor = sensor.vendor,
                    version = sensor.version,
                    stringType = stringType,
                    maxRange = sensor.maximumRange,
                    resolution = sensor.resolution,
                    power = sensor.power
                )
            }
        }

        if (targetSensors.isEmpty()) {
            Log.d(TAG, "No matching light or color sensors were found on this device")
            Log.d(TAG, "Available sensor types (Android constants): ${allSensors.map { it.type }.distinct().sorted()}")
        }

        return targetSensors
            .sortedWith(compareBy({ it.type.ordinal }, { it.name.lowercase() }, { it.vendor.lowercase() }))
    }

    fun observe(sensorInfo: SensorInfo, samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL): Flow<SensorReading> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor != sensorInfo.sensor) {
                    return
                }
                trySend(buildReading(sensorInfo, event))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor == sensorInfo.sensor) {
                    Log.d(TAG, "Accuracy for ${sensor.name} (${sensor.type}) changed to $accuracy")
                }
            }
        }

        val registered = sensorManager.registerListener(
            listener,
            sensorInfo.sensor,
            samplingPeriodUs
        )

        if (!registered) {
            Log.w(TAG, "Failed to register listener for sensor ${sensorInfo.name}")
            close()
            return@callbackFlow
        }

        Log.d(TAG, "Registered listener for sensor ${sensorInfo.name}")

        awaitClose {
            sensorManager.unregisterListener(listener)
            Log.d(TAG, "Unregistered listener for sensor ${sensorInfo.name}")
        }
    }

    private fun buildReading(sensorInfo: SensorInfo, event: SensorEvent): SensorReading {
        val rawValues = event.values.copyOf()
        val converted = mutableListOf(
            ConvertedValue(
                name = "Raw",
                value = formatRawValues(rawValues),
                conversionType = ConversionType.RAW
            )
        )

        when (sensorInfo.type) {
            SensorType.COLOR -> converted += buildColorConversions(sensorInfo, rawValues)
            SensorType.LIGHT -> converted += buildLightConversions(rawValues)
            SensorType.UNKNOWN -> Unit
        }

        return SensorReading(
            sensorInfo = sensorInfo,
            timestamp = event.timestamp,
            accuracy = event.accuracy,
            rawValues = rawValues,
            convertedValues = converted
        )
    }

    private fun buildColorConversions(sensorInfo: SensorInfo, rawValues: FloatArray): List<ConvertedValue> {
        if (rawValues.size < 3) {
            return emptyList()
        }

        val (r, g, b) = ColorConverter.toNormalizedRgb(rawValues, sensorInfo.maxRange)
        val conversions = mutableListOf<ConvertedValue>()

        conversions += ConvertedValue(
            name = "Normalized RGB",
            value = "R ${formatValue(r, width = 8, decimals = 4)}\nG ${formatValue(g, width = 8, decimals = 4)}\nB ${formatValue(b, width = 8, decimals = 4)}",
            conversionType = ConversionType.LINEAR
        )

        val (h, s, v) = ColorConverter.rgbToHsv(r, g, b)
        conversions += ConvertedValue(
            name = "HSV",
            value = "H ${formatValue(h, width = 7, decimals = 1)}°\nS ${formatValue(s, width = 8, decimals = 3)}\nV ${formatValue(v, width = 8, decimals = 3)}",
            conversionType = ConversionType.COLOR_SPACE
        )

        val (x, y, z) = ColorConverter.rgbToXyz(r, g, b)
        conversions += ConvertedValue(
            name = "XYZ",
            value = "X ${formatValue(x, width = 9, decimals = 4)}\nY ${formatValue(y, width = 9, decimals = 4)}\nZ ${formatValue(z, width = 9, decimals = 4)}",
            conversionType = ConversionType.COLOR_SPACE
        )

        val (l, a, bLab) = ColorConverter.xyzToLab(x, y, z)
        conversions += ConvertedValue(
            name = "LAB",
            value = "L ${formatValue(l, width = 8, decimals = 2)}\na ${formatValue(a, width = 8, decimals = 2)}\nb ${formatValue(bLab, width = 8, decimals = 2)}",
            conversionType = ConversionType.COLOR_SPACE
        )

        val clear = rawValues.getOrNull(3)
        if (clear != null) {
            conversions += ConvertedValue(
                name = "Clear Channel",
                value = formatValue(clear, width = 10, decimals = 4),
                conversionType = ConversionType.LINEAR
            )
        }

        return conversions
    }

    private fun buildLightConversions(rawValues: FloatArray): List<ConvertedValue> {
        val lux = rawValues.firstOrNull() ?: return emptyList()
        val conversions = mutableListOf<ConvertedValue>()

        conversions += ConvertedValue(
            name = "Illuminance",
            value = formatValueWithUnit(lux, "lux", unitWidth = 6, valueWidth = 10, decimals = 2),
            conversionType = ConversionType.LINEAR
        )

        val footCandles = lux / 10.764f
        conversions += ConvertedValue(
            name = "Foot-candle",
            value = formatValueWithUnit(footCandles, "fc", unitWidth = 6, valueWidth = 10, decimals = 2),
            conversionType = ConversionType.LINEAR
        )

        val nits = lux / kotlin.math.PI.toFloat()
        conversions += ConvertedValue(
            name = "Luminance",
            value = formatValueWithUnit(nits, "nits", unitWidth = 6, valueWidth = 10, decimals = 2),
            conversionType = ConversionType.LINEAR
        )

        val ev100 = if (lux > 0f) {
            val ev = kotlin.math.ln((lux * 8f).toDouble()) / kotlin.math.ln(2.0)
            ev.toFloat()
        } else {
            Float.NEGATIVE_INFINITY
        }
        conversions += ConvertedValue(
            name = "Exposure Value",
            value = if (ev100.isFinite()) {
                formatValueWithUnit(ev100, "EV@ISO100", unitWidth = 10, valueWidth = 8, decimals = 2)
            } else {
                "      -∞ ${"EV@ISO100".padEnd(10)}"
            },
            conversionType = ConversionType.NON_LINEAR
        )

        val qualitative = when {
            lux < 10f -> "Night"
            lux < 50f -> "Twilight"
            lux < 200f -> "Indoor"
            lux < 500f -> "Office"
            lux < 1000f -> "Cloudy"
            lux < 25000f -> "Sunny"
            else -> "Direct Sun"
        }
        conversions += ConvertedValue(
            name = "Scene",
            value = qualitative,
            conversionType = ConversionType.NON_LINEAR
        )

        return conversions
    }

    private fun classifySensor(sensor: Sensor): SensorType {
        val stringType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            sensor.stringType ?: ""
        } else {
            ""
        }

        return when {
            sensor.type == Sensor.TYPE_LIGHT || stringType.contains("light", ignoreCase = true) -> SensorType.LIGHT
            matchesColorSensor(sensor, stringType) -> SensorType.COLOR
            else -> SensorType.UNKNOWN
        }
    }

    private fun matchesColorSensor(sensor: Sensor, stringType: String): Boolean {
        COLOR_SENSOR_TYPE?.let { colorType ->
            if (sensor.type == colorType) {
                return true
            }
        }
        return stringType.contains("color", ignoreCase = true) ||
            stringType.contains("rgb", ignoreCase = true) ||
            sensor.name.contains("color", ignoreCase = true) ||
            sensor.name.contains("rgb", ignoreCase = true)
    }
}
