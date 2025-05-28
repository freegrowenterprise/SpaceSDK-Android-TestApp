package com.growspace.testapp.pages.rtls

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import com.growspace.sdk.SpaceUwb
import com.growspace.sdk.rtls.filter.RtlsFilterType

@Composable
fun RTLSPage(navController: NavHostController, viewModel: DeviceCoordinateViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val spaceUWB = remember(context, activity) {
        activity?.let { SpaceUwb("", context, it) }
    }

    var rowInput by remember { mutableStateOf("5") }
    var columnInput by remember { mutableStateOf("5") }

    var rowCount by remember { mutableStateOf(5) }
    var columnCount by remember { mutableStateOf(5) }

    val showGrid = remember { mutableStateOf(true) }

    val points by remember(viewModel.deviceCoordinates) {
        derivedStateOf {
            viewModel.deviceCoordinates
                .filterKeys { it.startsWith("FGU-") }
                .mapNotNull { (name, coord) ->
                    val x = coord.x
                    val y = coord.y
                    name to Offset(y, x)
                }
        }
    }

    val rtlsPoint = viewModel.currentRtlsLocation
    val allPoints: List<Pair<String, Offset>> = remember(rtlsPoint, points) {
        if (rtlsPoint != null) points + ("내 위치" to Offset(
            rtlsPoint.y,
            rtlsPoint.x
        ))
        else points
    }
    val isLoading = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RTLS Example", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(40.dp))

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            onClick = {
                navController.navigate("uwbSetting")
            }
        ) {
            Text("UWB 장비 위치 설정")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = rowInput,
                onValueChange = {
                    if (it.length <= 2 && (it.toIntOrNull() ?: 0) <= 10) rowInput = it
                },
                label = { Text("세로 (최대 10)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Text(text = "X", style = MaterialTheme.typography.bodyLarge)

            OutlinedTextField(
                value = columnInput,
                onValueChange = {
                    if (it.length <= 2 && (it.toIntOrNull() ?: 0) <= 10) columnInput = it
                },
                label = { Text("가로 (최대 10)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            Button(onClick = {
                val row = rowInput.toIntOrNull()?.coerceIn(1, 10) ?: rowCount
                val col = columnInput.toIntOrNull()?.coerceIn(1, 10) ?: columnCount
                rowCount = row
                columnCount = col
                showGrid.value = true
            }) {
                Text("설정")
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "왼쪽 위 기준 (0, 0)   단위 : m",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (showGrid.value) {
            GridWithDots(
                rowCount = rowCount,
                columnCount = columnCount,
                points = allPoints
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val hasNoCoordinates = viewModel.deviceCoordinates
            .filterKeys { it.startsWith("FGU-") }
            .isEmpty()

        val distances = viewModel.anchorDistances

        if (isLoading.value) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "위치 측정 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            Column {
                Text("실시간 거리 정보", style = MaterialTheme.typography.titleMedium)

                distances.forEach { (deviceName, distance) ->
                    Text("[$deviceName] → ${String.format("%.2f", distance)} m")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                modifier = Modifier
                    .padding(vertical = 8.dp),
                onClick = {
                    spaceUWB?.stopUwbRanging()
                }) {
                Text("위치 확인 중지")
            }
            Button(
                onClick = {
                    if (hasNoCoordinates) {
                        Toast.makeText(context, "UWB 장비 위치 설정 먼저 해주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        isLoading.value = true

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
                                Log.d("RTLS", "결과 위치: ${result.x}, ${result.y}, ${result.z}")

                                val x = result.x.toFloat()
                                val y = result.y.toFloat()

                                viewModel.setCurrentLocation(Offset(x, y))
                                isLoading.value = false
                            },
                            onFail = { error ->
                                Log.e("RTLS", "실패: $error")
                                isLoading.value = false
                            },
                            onDeviceRanging = { distanceMap ->
                                Log.d("RTLS", "장치 거리: $distanceMap")
                                viewModel.updateAnchorDistances(distanceMap)
                                isLoading.value = false
                            }
                        )
                    }
                },
                modifier = Modifier
                    .padding(vertical = 8.dp),
                colors = if (hasNoCoordinates) {
                    ButtonDefaults.buttonColors(containerColor = Color.Gray)
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text("위치 확인 시작")
            }
        }
    }
}

@Composable
fun GridWithDots(
    rowCount: Int,
    columnCount: Int,
    points: List<Pair<String, Offset>>
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .border(1.dp, Color.Gray)
    ) {
        val cellSize = maxWidth / columnCount
        val totalHeight = cellSize * rowCount
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .width(maxWidth * 0.95f)
                .height(totalHeight)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSizePx = with(density) { cellSize.toPx() }

                for (row in 0 until rowCount) {
                    for (col in 0 until columnCount) {
                        val left = col * cellSizePx
                        val top = row * cellSizePx
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(left, top),
                            size = Size(cellSizePx, cellSizePx),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                points.forEach { (name, point) ->
                    val x = point.y * cellSizePx
                    val y = point.x * cellSizePx

                    drawCircle(
                        color = Color.Red,
                        radius = 10f,
                        center = Offset(x, y)
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        name,
                        x,
                        y - 12f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 24f
                            isAntiAlias = true
                        }
                    )
                }
            }
        }
    }
}