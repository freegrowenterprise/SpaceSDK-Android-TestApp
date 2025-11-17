package com.growspace.testapp.pages.rtls

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.growspace.sdk.SpaceUwb
import com.growspace.sdk.rtls.filter.RtlsFilterType
import com.growspace.testapp.MQTTManager

@Composable
fun RTLSPage(navController: NavHostController, viewModel: DeviceCoordinateViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val spaceUWB = remember(context, activity) {
        activity?.let { SpaceUwb(context, it) }
    }

    val mqttManager = remember(context) { MQTTManager(context) }
    val deviceId = remember {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "Android-Unknown"
    }

    var mqttConnected by remember { mutableStateOf(false) }

    // MQTT 자동 연결
    LaunchedEffect(mqttManager) {
        mqttManager.connect(
            host = "3.38.52.15",
            port = 1883,
            username = "freegrow",
            password = "gogrow!",
            onSuccess = {
                mqttConnected = true
                Log.d("RTLS", "✅ MQTT Connected")
            },
            onFailure = { error ->
                mqttConnected = false
                Log.e("RTLS", "❌ MQTT Connection failed: ${error.message}")
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mqttManager.disconnect()
        }
    }

    var isRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Ready to start UWB") }
    var distanceText by remember { mutableStateOf("Distance: -") }
    var coordinateText by remember { mutableStateOf("Coordinate: -") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "RTLS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // UWB Equipment Positioning Button
        Button(
            onClick = {
                navController.navigate("uwbSetting")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) {
            Text("UWB equipment positioning", color = Color.White)
        }

        // Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning && distanceText == "Distance: -") {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = statusText,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        // Distance Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = distanceText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }

        // Coordinate Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = coordinateText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.sp,
                color = Color.DarkGray
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stop Button
            Button(
                onClick = {
                    isRunning = false
                    statusText = "Stopped"
                    distanceText = "Distance: -"
                    coordinateText = "Coordinate: -"
                    spaceUWB?.stopUwbRanging()
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
            ) {
                Text("Stop", color = Color.White)
            }

            // Start Button
            Button(
                onClick = {
                    val hasNoCoordinates = viewModel.deviceCoordinates
                        .filterKeys { it.startsWith("FGU-") }
                        .isEmpty()

                    if (hasNoCoordinates) {
                        Toast.makeText(context, "Please set the location of UWB equipment first.", Toast.LENGTH_SHORT).show()
                    } else {
                        isRunning = true
                        statusText = "Starting..."

                        val anchorPositionMap = viewModel.deviceCoordinates
                            .filterKeys { it.startsWith("FGU-") }
                            .mapNotNull { (key, coord) ->
                                val x = coord.x.toDouble()
                                val y = coord.y.toDouble()
                                key to Triple(x, y, 1.0)
                            }
                            .toMap()

                        spaceUWB?.startUwbRtls(
                            anchorPositionMap = anchorPositionMap,
                            zCorrection = 1.0f,
                            maximumConnectionCount = 4,
                            replacementDistanceThreshold = 8f,
                            isConnectStrongestSignalFirst = true,
                            filterType = RtlsFilterType.MOVING_AVERAGE,
                            onResult = { result ->
                                coordinateText = String.format(
                                    "Coordinate:\n  X: %.2f m\n  Y: %.2f m",
                                    result.x,
                                    result.y
                                )
                                viewModel.setCurrentLocation(Offset(result.x.toFloat(), result.y.toFloat()))
                                statusText = "UWB Running..."

                                // MQTT: 실시간 좌표 전송
                                mqttManager.publishCoordinate(
                                    deviceId = deviceId,
                                    x = result.x,
                                    y = result.y
                                )
                            },
                            onFail = { error ->
                                Log.e("RTLS", "Failed: $error")
                                statusText = "Error: $error"
                            },
                            onDeviceRanging = { distanceMap ->
                                val distanceLines = distanceMap.map { (name, distance) ->
                                    "  [$name] → ${String.format("%.2f", distance)}m"
                                }.joinToString("\n")
                                distanceText = "Distance:\n$distanceLines"

                                // MQTT: 앵커 간 거리 전송 (각 앵커마다 개별 전송)
                                distanceMap.forEach { (anchorId, distance) ->
                                    mqttManager.publishDistance(
                                        deviceId = deviceId,
                                        anchorId = anchorId,
                                        distance = distance
                                    )
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("Start", color = Color.White)
            }
        }
    }
}
