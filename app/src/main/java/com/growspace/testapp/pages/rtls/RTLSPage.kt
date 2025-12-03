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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

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

    // 앵커별 마지막 업데이트 시각 추적
    val anchorLastUpdateTime = remember { mutableStateMapOf<String, Long>() }
    val currentDistanceMap = remember { mutableStateMapOf<String, Float>() }
    val coroutineScope = rememberCoroutineScope()

    // 위치 히스토리 및 추정 관련
    val positionHistory = remember { mutableStateListOf<Pair<Offset, Long>>() }
    var lastEstimationStartTime by remember { mutableStateOf<Long?>(null) }
    var lastThreeAnchorTime by remember { mutableStateOf<Long?>(null) }  // 마지막으로 3개 앵커였던 시각
    val maxHistoryCount = 10

    // IMU 센서 관련
    var currentHeading by remember { mutableStateOf(0.0) }  // 현재 진행 방향 (라디안)
    var isMoving by remember { mutableStateOf(false) }  // 이동 중 여부

    // SensorManager 초기화
    val sensorManager = remember { context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager }
    val gyroscope = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    // IMU 센서 리스너
    val sensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        // 자이로: z축 회전 누적
                        val rotationZ = event.values[2]
                        currentHeading += rotationZ * 0.1
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        // 가속도: 이동 여부 감지
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val accelMagnitude = kotlin.math.sqrt((x * x + y * y + z * z).toDouble())
                        isMoving = accelMagnitude > 10.0  // 중력 제외한 가속도
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    // IMU 센서 시작/중지 관리
    DisposableEffect(isRunning) {
        if (isRunning) {
            gyroscope?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            accelerometer?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        onDispose {
            sensorManager.unregisterListener(sensorListener)
            isMoving = false
            currentHeading = 0.0
        }
    }

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
                    anchorLastUpdateTime.clear()
                    currentDistanceMap.clear()
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
                        anchorLastUpdateTime.clear()
                        currentDistanceMap.clear()
                        positionHistory.clear()
                        lastEstimationStartTime = null

                        val anchorPositionMap = viewModel.deviceCoordinates
                            .filterKeys { it.startsWith("FGU-") }
                            .mapNotNull { (key, coord) ->
                                val x = coord.x.toDouble()
                                val y = coord.y.toDouble()
                                key to Triple(x, y, 1.0)
                            }
                            .toMap()

                        // 1초마다 오래된 앵커 제거
                        coroutineScope.launch {
                            while (isRunning) {
                                delay(1000)
                                val now = System.currentTimeMillis()

                                val staleAnchors = anchorLastUpdateTime.filter { (_, lastUpdate) ->
                                    now - lastUpdate > 2000  // 2초로 변경
                                }.keys.toList()

                                staleAnchors.forEach { anchorId ->
                                    anchorLastUpdateTime.remove(anchorId)
                                    currentDistanceMap.remove(anchorId)
                                }
                            }
                        }

                        spaceUWB?.startUwbRtls(
                            anchorPositionMap = anchorPositionMap,
                            zCorrection = 1.0f,
                            maximumConnectionCount = 4,
                            replacementDistanceThreshold = 50f,
                            isConnectStrongestSignalFirst = true,
                            filterType = RtlsFilterType.MOVING_AVERAGE,
                            onResult = { result ->
                                // [3개 이상] 정상 RTLS 결과
                                if (currentDistanceMap.size >= 3) {
                                    val position = Offset(result.x.toFloat(), result.y.toFloat())
                                    coordinateText = String.format(
                                        "Coordinate:\n  X: %.2f m\n  Y: %.2f m",
                                        result.x,
                                        result.y
                                    )
                                    viewModel.setCurrentLocation(position)
                                    addPositionToHistory(positionHistory, position, maxHistoryCount)
                                    statusText = "UWB Running..."
                                    lastEstimationStartTime = null  // 추정 모드 종료
                                    lastThreeAnchorTime = System.currentTimeMillis()  // 3개 앵커 시각 기록

                                    // MQTT: 실시간 좌표 전송
                                    mqttManager.publishCoordinate(
                                        deviceId = deviceId,
                                        x = result.x,
                                        y = result.y,
                                        anchorCount = currentDistanceMap.size
                                    )
                                }
                            },
                            onFail = { error ->
                                Log.e("RTLS", "Failed: $error")
                                statusText = "Error: $error"
                            },
                            onDeviceRanging = onDeviceRanging@{ distanceMap ->
                                val now = System.currentTimeMillis()

                                // 각 앵커의 업데이트 시각 기록
                                distanceMap.forEach { (anchorId, distance) ->
                                    anchorLastUpdateTime[anchorId] = now
                                    currentDistanceMap[anchorId] = distance
                                }

                                val distanceLines = currentDistanceMap.map { (name, distance) ->
                                    "  [$name] → ${String.format("%.2f", distance)}m"
                                }.joinToString("\n")
                                distanceText = "Distance:\n$distanceLines"

                                // MQTT: 앵커 간 거리 전송
                                distanceMap.forEach { (anchorId, distance) ->
                                    mqttManager.publishDistance(
                                        deviceId = deviceId,
                                        anchorId = anchorId,
                                        distance = distance
                                    )
                                }

                                val anchorCount = currentDistanceMap.size

                                // 앵커 개수별 처리
                                when {
                                    anchorCount >= 3 -> {
                                        // onResult에서 처리됨
                                    }
                                    anchorCount == 2 -> {
                                        // [2개] 교점 계산 - 3개에서 2개로 전환 시 1초 대기
                                        // 3개 앵커였다가 2개로 줄어든 경우, 1초 대기
                                        if (lastThreeAnchorTime != null && now - lastThreeAnchorTime!! < 1000) {
                                            // 1초 안에 다시 3개가 될 수 있으므로 대기
                                            return@onDeviceRanging
                                        }

                                        // 2개 앵커는 시간 제한 없이 계속 유지
                                        // 앵커 데이터 준비
                                        val anchorsData = currentDistanceMap.map { (anchorId, distance) ->
                                            val anchorPos = viewModel.deviceCoordinates[anchorId]
                                            if (anchorPos != null) {
                                                Triple(anchorId, anchorPos, distance.toDouble())
                                            } else {
                                                null
                                            }
                                        }.filterNotNull()

                                        val lastPos = viewModel.currentRtlsLocation
                                        val estimatedPos = calculatePositionWith2Anchors(
                                            anchorsData,
                                            lastPos
                                        )

                                        if (estimatedPos != null) {
                                            coordinateText = String.format(
                                                "Coordinate:\n  X: %.2f m\n  Y: %.2f m",
                                                estimatedPos.x,
                                                estimatedPos.y
                                            )
                                            viewModel.setCurrentLocation(estimatedPos)
                                            statusText = "UWB Running (2 anchors)..."

                                            mqttManager.publishCoordinate(
                                                deviceId = deviceId,
                                                x = estimatedPos.x.toDouble(),
                                                y = estimatedPos.y.toDouble(),
                                                anchorCount = 2
                                            )
                                        } else {
                                            viewModel.setCurrentLocation(null)
                                            coordinateText = "Coordinate: -"
                                        }
                                    }
                                    else -> {
                                        // [1개 이하] 좌표 삭제
                                        viewModel.setCurrentLocation(null)
                                        coordinateText = "Coordinate: -"
                                    }
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

// MARK: - Helper Functions

// 위치 히스토리에 추가
fun addPositionToHistory(
    positionHistory: MutableList<Pair<Offset, Long>>,
    position: Offset,
    maxCount: Int
) {
    val now = System.currentTimeMillis()
    positionHistory.add(Pair(position, now))

    // 최대 개수 유지
    while (positionHistory.size > maxCount) {
        positionHistory.removeAt(0)
    }
}

// 속도 벡터 계산 (m/s)
fun calculateVelocity(positionHistory: List<Pair<Offset, Long>>): Offset? {
    if (positionHistory.size < 2) return null

    val recent = positionHistory.takeLast(5)  // 최근 5개
    if (recent.size < 2) return null

    val latest = recent.last()
    val oldest = recent.first()

    val timeDiff = (latest.second - oldest.second) / 1000.0  // ms to seconds
    if (timeDiff <= 0) return null

    val dx = latest.first.x - oldest.first.x
    val dy = latest.first.y - oldest.first.y

    return Offset((dx / timeDiff).toFloat(), (dy / timeDiff).toFloat())
}

// 2개 앵커: 교점 계산 (속도 벡터 + IMU 활용)
fun calculatePositionWith2Anchors(
    anchors: List<Triple<String, Offset, Double>>,  // (anchorId, position, distance)
    lastPosition: Offset?
): Offset? {
    if (anchors.size != 2) return null

    val (_, pos1, r1) = anchors[0]
    val (_, pos2, r2) = anchors[1]

    val x1 = pos1.x.toDouble()
    val y1 = pos1.y.toDouble()
    val x2 = pos2.x.toDouble()
    val y2 = pos2.y.toDouble()

    // 두 원의 교점 계산
    val d = kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))

    // 원이 너무 멀거나 겹치지 않으면 실패
    if (d > r1 + r2 || d < kotlin.math.abs(r1 - r2) || d == 0.0) {
        return null
    }

    val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
    val h = kotlin.math.sqrt(r1 * r1 - a * a)

    val px = x1 + a * (x2 - x1) / d
    val py = y1 + a * (y2 - y1) / d

    // 두 교점
    val intersection1 = Offset(
        (px + h * (y2 - y1) / d).toFloat(),
        (py - h * (x2 - x1) / d).toFloat()
    )
    val intersection2 = Offset(
        (px - h * (y2 - y1) / d).toFloat(),
        (py + h * (x2 - x1) / d).toFloat()
    )

    // 교점 선택: 마지막 위치에서 가장 가까운 점 선택
    return if (lastPosition != null) {
        val dist1 = distanceBetween(lastPosition, intersection1)
        val dist2 = distanceBetween(lastPosition, intersection2)
        if (dist1 < dist2) intersection1 else intersection2
    } else {
        intersection1
    }
}

// 1개 앵커: Dead Reckoning
fun calculatePositionWith1Anchor(
    timeSinceEstimationStart: Double,  // seconds
    lastPosition: Offset?,
    velocity: Offset?
): Offset? {
    if (lastPosition == null || velocity == null) return null

    // 최대 2초까지만
    if (timeSinceEstimationStart > 2.0) return null

    // 마지막 위치 + 속도 * 시간
    return Offset(
        lastPosition.x + velocity.x * timeSinceEstimationStart.toFloat(),
        lastPosition.y + velocity.y * timeSinceEstimationStart.toFloat()
    )
}

// 두 점 사이 거리
fun distanceBetween(p1: Offset, p2: Offset): Double {
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
}
