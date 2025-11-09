package com.example.sensorapp

import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sensorapp.ui.theme.SensorReaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var sensorReader: SensorReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorReader = SensorReader(this)

        setContent {
            SensorReaderTheme {
                SensorApp(sensorReader)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorApp(sensorReader: SensorReader) {
    val sensorReadings = remember { mutableStateMapOf<Int, SensorReading>() }
    var sensors by remember { mutableStateOf<List<SensorInfo>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sensors = sensorReader.discoverSensors()
        initialized = true

        if (sensors.isEmpty()) {
            Log.d("MainActivity", "No color or light sensors detected on this device")
        }

        sensors.forEach { sensorInfo ->
            launch {
                sensorReader.observe(sensorInfo, SensorManager.SENSOR_DELAY_UI).collect { reading ->
                    sensorReadings[sensorInfo.sensor.hashCode()] = reading
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Reader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            if (!initialized) {
                CenteredText("Initializing sensors...")
            } else if (sensors.isEmpty()) {
                CenteredText("No color or light sensors detected")
            } else {
                SensorList(sensors, sensorReadings)
            }
        }
    }
}

@Composable
fun CenteredText(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SensorList(sensors: List<SensorInfo>, readings: Map<Int, SensorReading>) {
    val grouped = sensors.groupBy { it.type }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (type, sensorsOfType) ->
            item {
                TypeHeader(type)
            }
            items(sensorsOfType) { sensorInfo ->
                SensorCard(sensorInfo, readings[sensorInfo.sensor.hashCode()])
            }
        }
    }
}

@Composable
fun TypeHeader(type: SensorType) {
    Text(
        text = when (type) {
            SensorType.COLOR -> "Color Sensors"
            SensorType.LIGHT -> "Light Sensors"
            SensorType.UNKNOWN -> "Other Sensors"
        },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SensorCard(sensorInfo: SensorInfo, reading: SensorReading?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = sensorInfo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Vendor: ${sensorInfo.vendor}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Type: ${sensorInfo.stringType}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Version: ${sensorInfo.version} | Power: %.2f mA".format(sensorInfo.power),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Text(
                text = "Range: %.2f | Resolution: %.4f".format(
                    sensorInfo.maxRange,
                    sensorInfo.resolution
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if (reading != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                val accuracy = when (reading.accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
                    else -> "Unknown"
                }
                Text(
                    text = "Accuracy: $accuracy",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                reading.convertedValues.forEach { converted ->
                    ConvertedValueRow(converted)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Waiting for data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun ConvertedValueRow(converted: ConvertedValue) {
    if (converted.conversionType == ConversionType.RAW) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ConversionTypeIndicator(converted.conversionType)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = converted.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (converted.value.contains("\n")) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    converted.value.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = converted.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ConversionTypeIndicator(converted.conversionType)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = converted.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (converted.value.contains("\n")) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    converted.value.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = converted.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun ConversionTypeIndicator(type: ConversionType) {
    val (color, label) = when (type) {
        ConversionType.RAW -> Color(0xFF9E9E9E) to "R"
        ConversionType.LINEAR -> Color(0xFF4CAF50) to "L"
        ConversionType.NON_LINEAR -> Color(0xFFFF9800) to "N"
        ConversionType.COLOR_SPACE -> Color(0xFF2196F3) to "C"
    }

    Box(
        modifier = Modifier
            .background(color, shape = RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

