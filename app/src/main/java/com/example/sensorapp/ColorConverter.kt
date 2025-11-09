package com.example.sensorapp

import kotlin.math.cbrt
import kotlin.math.pow

object ColorConverter {

    fun toNormalizedRgb(values: FloatArray, maxRange: Float): Triple<Float, Float, Float> {
        val red = values.getOrNull(0)?.coerceAtLeast(0f) ?: 0f
        val green = values.getOrNull(1)?.coerceAtLeast(0f) ?: 0f
        val blue = values.getOrNull(2)?.coerceAtLeast(0f) ?: 0f
        if (maxRange <= 0f) {
            return Triple(red, green, blue)
        }
        return Triple(
            (red / maxRange).coerceIn(0f, 1f),
            (green / maxRange).coerceIn(0f, 1f),
            (blue / maxRange).coerceIn(0f, 1f)
        )
    }

    fun rgbToXyz(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        // sRGB D65 conversion
        val linearR = linearize(r)
        val linearG = linearize(g)
        val linearB = linearize(b)

        val x = (linearR * 0.4124f + linearG * 0.3576f + linearB * 0.1805f)
        val y = (linearR * 0.2126f + linearG * 0.7152f + linearB * 0.0722f)
        val z = (linearR * 0.0193f + linearG * 0.1192f + linearB * 0.9505f)
        return Triple(x, y, z)
    }

    fun xyzToLab(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val xr = x / REFERENCE_X
        val yr = y / REFERENCE_Y
        val zr = z / REFERENCE_Z

        val fx = f(xr)
        val fy = f(yr)
        val fz = f(zr)

        val l = 116f * fy - 16f
        val a = 500f * (fx - fy)
        val b = 200f * (fy - fz)

        return Triple(l, a, b)
    }

    fun rgbToHsv(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> ((g - b) / delta) % 6f
            max == g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f

        val saturation = if (max == 0f) 0f else delta / max
        val value = max

        return Triple(if (hue < 0f) hue + 360f else hue, saturation, value)
    }

    private fun linearize(channel: Float): Float {
        return if (channel <= 0.04045f) {
            channel / 12.92f
        } else {
            ((channel + 0.055f) / 1.055f).pow(2.4f)
        }
    }

    private fun f(t: Float): Float {
        return if (t > EPSILON) {
            cbrt(t.toDouble()).toFloat()
        } else {
            (KAPPA * t + 16f) / 116f
        }
    }

    private const val EPSILON = 216f / 24389f
    private const val KAPPA = 24389f / 27f
    private const val REFERENCE_X = 0.95047f
    private const val REFERENCE_Y = 1.0f
    private const val REFERENCE_Z = 1.08883f
}
