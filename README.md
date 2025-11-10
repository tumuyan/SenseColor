# Sense Color
这是一个由AI构建的简单应用，旨在为用户呈现更多环境光/色彩传感器的信息。事实上由于Android系统API的局限性，我们并不能做到Readme中阐述的目的，但是毫无疑问这个应用确实可以比其他应用显示更多ALS的信息

An Android application that reads and displays raw sensor data from color sensors and ambient light sensors on the device.
> **Note:** This is a simple application built with the assistance of AI. Due to the limitations of Android's system APIs, it may not fully achieve all the objectives described in this README. However, it undoubtedly displays more detailed information from Ambient Light Sensors (ALS) than many other applications.

## Features

- 🎨 **Color Sensor Support**: Detects and reads RGB color sensors
- 💡 **Light Sensor Support**: Detects and reads ambient light sensors
- 📊 **Multiple Sensor Support**: Handles multiple sensors of the same type
- 🔄 **Real-time Updates**: Displays sensor readings in real-time
- 🎯 **Value Conversions**: Converts raw sensor values to various formats

### Conversion Types

The app displays different types of converted values with visual indicators:

- **[R] Raw Values**: Original sensor readings
- **[L] Linear Conversions**: Direct linear transformations (e.g., normalized RGB, lux, foot-candles)
- **[N] Non-Linear Conversions**: Non-linear transformations (e.g., brightness levels, exposure values)
- **[C] Color Space Conversions**: Color space transformations (e.g., HSV, XYZ, LAB)

### Color Sensor Conversions

- Normalized RGB (0-1 range)
- HSV (Hue, Saturation, Value)
- XYZ color space
- LAB color space
- Clear channel (if available)

### Light Sensor Conversions

- Illuminance (lux)
- Foot-candles (fc)
- Luminance (nits)
- Exposure Value (EV@ISO100)
- Qualitative scene description (Night, Twilight, Indoor, Office, Cloudy, Sunny, Direct Sun)

## Technical Details

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)

### Architecture

- `MainActivity`: Main entry point using Jetpack Compose
- `SensorReader`: Handles sensor discovery and data streaming
- `ColorConverter`: Utilities for color space conversions
- `SensorData`: Data models for sensor information and readings

### Sensor Detection

The app uses Android's `SensorManager` API to detect:

- `TYPE_LIGHT`: Ambient light sensors
- `TYPE_COLOR`: Color sensors (API 26+)
- Name/type-based detection for devices with non-standard sensor implementations

## Building

```bash
./gradlew assembleDebug
```

## Installation

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run it on a device or emulator.

## Debug Logging

The app logs detailed sensor information to logcat under the tag `SensorReader`. Use the following command to view logs:

```bash
adb logcat -s SensorReader
```

## Requirements

- Android device or emulator with API 24+
- Color or light sensors (not all devices have these sensors)

## Notes

- If no color or light sensors are detected, the app will display a message indicating no sensors were found
- Debug information is logged to help diagnose sensor availability issues
- Sensor readings update in real-time with configurable sampling rates
