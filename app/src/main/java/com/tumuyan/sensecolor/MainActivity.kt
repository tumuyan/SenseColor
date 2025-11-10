package com.tumuyan.sensecolor

import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.res.stringResource
import com.tumuyan.sensecolor.R
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tumuyan.sensecolor.ui.theme.SensorReaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var sensorReader: SensorReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorReader = SensorReader(this)

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastVersionCode = preferences.getLong(KEY_LAST_VERSION_CODE, -1L)
        val isFirstLaunchOrUpgrade = lastVersionCode != currentVersionCode

        setContent {
            SensorReaderTheme {
                SensorApp(
                    sensorReader = sensorReader,
                    shouldShowSplash = true,
                    isFirstLaunchOrUpgrade = isFirstLaunchOrUpgrade,
                    onSplashDismissed = {
                        if (isFirstLaunchOrUpgrade) {
                            preferences.edit()
                                .putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
                                .apply()
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "sense_color_preferences"
        private const val KEY_LAST_VERSION_CODE = "last_version_code"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorApp(
    sensorReader: SensorReader,
    shouldShowSplash: Boolean,
    isFirstLaunchOrUpgrade: Boolean,
    onSplashDismissed: () -> Unit
) {
    val sensorReadings = remember { mutableStateMapOf<Int, SensorReading>() }
    var sensors by remember { mutableStateOf<List<SensorInfo>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    val showSplash = rememberSaveable { mutableStateOf(shouldShowSplash) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
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

        if (showSplash.value) {
            SplashScreen(isFirstLaunchOrUpgrade = isFirstLaunchOrUpgrade) {
                showSplash.value = false
                onSplashDismissed()
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
fun SensorList(
    sensors: List<SensorInfo>,
    readings: Map<Int, SensorReading>
) {
    val expandedSensors = remember { mutableStateMapOf<Int, Boolean>() }
    
    LaunchedEffect(sensors) {
        sensors.forEach { sensorInfo ->
            if (sensorInfo.sensor.hashCode() !in expandedSensors) {
                expandedSensors[sensorInfo.sensor.hashCode()] = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = sensors,
            key = { it.sensor.hashCode() }
        ) { sensorInfo ->
            val sensorId = sensorInfo.sensor.hashCode()
            val isExpanded = expandedSensors[sensorId] ?: true
            SensorCard(
                sensorInfo = sensorInfo,
                reading = readings[sensorId],
                isExpanded = isExpanded,
                onToggleExpanded = {
                    expandedSensors[sensorId] = !isExpanded
                }
            )
        }
    }
}


@Composable
fun SensorCard(
    sensorInfo: SensorInfo,
    reading: SensorReading?,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "sensorCardArrow"
    )
    val rawFirstValue = reading?.convertedValues
        ?.firstOrNull { it.conversionType == ConversionType.RAW }
        ?.value
        ?.lines()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sensorInfo.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(arrowRotation)
                    )
                }

                if (!isExpanded && rawFirstValue != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        ConversionTypeIndicator(ConversionType.RAW)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = rawFirstValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = "Vendor: ${sensorInfo.vendor}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Type: ${sensorInfo.stringType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Version: ${sensorInfo.version} | Power: %.2f mA".format(sensorInfo.power),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Range: %.2f | Resolution: %.4f".format(
                            sensorInfo.maxRange,
                            sensorInfo.resolution
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConvertedValueRow(converted: ConvertedValue) {
    if (converted.conversionType == ConversionType.RAW) {
        val lines = converted.value.lines()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
        val isSingleLine = lines.size <= 1

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConversionTypeIndicator(converted.conversionType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = converted.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isSingleLine && lines.isNotEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = lines.first(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (!isSingleLine && lines.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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

