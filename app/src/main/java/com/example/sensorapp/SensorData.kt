package com.example.sensorapp

import android.hardware.Sensor

data class SensorInfo(
    val sensor: Sensor,
    val type: SensorType,
    val name: String,
    val vendor: String,
    val version: Int,
    val stringType: String,
    val maxRange: Float,
    val resolution: Float,
    val power: Float
)

enum class SensorType {
    COLOR,
    LIGHT,
    UNKNOWN
}

data class SensorReading(
    val sensorInfo: SensorInfo,
    val timestamp: Long,
    val accuracy: Int,
    val rawValues: FloatArray,
    val convertedValues: List<ConvertedValue> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorReading

        if (sensorInfo != other.sensorInfo) return false
        if (timestamp != other.timestamp) return false
        if (accuracy != other.accuracy) return false
        if (!rawValues.contentEquals(other.rawValues)) return false
        if (convertedValues != other.convertedValues) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sensorInfo.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + accuracy
        result = 31 * result + rawValues.contentHashCode()
        result = 31 * result + convertedValues.hashCode()
        return result
    }
}

data class ConvertedValue(
    val name: String,
    val value: String,
    val conversionType: ConversionType
)

enum class ConversionType {
    RAW,
    LINEAR,
    NON_LINEAR,
    COLOR_SPACE
}

data class GroupedSensors(
    val sensorType: SensorType,
    val sensors: List<SensorReading>
)
