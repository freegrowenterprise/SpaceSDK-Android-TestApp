package com.growspace.testapp.pages.rtls

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@Composable
fun RTLSPage(navController: NavHostController, viewModel: DeviceCoordinateViewModel) {
    val context = LocalContext.current
    var rowInput by remember { mutableStateOf("5") }
    var columnInput by remember { mutableStateOf("5") }

    var rowCount by remember { mutableStateOf(5) }
    var columnCount by remember { mutableStateOf(5) }

    val showGrid = remember { mutableStateOf(true) }

    val points = viewModel.deviceCoordinates
        .filterKeys { it.startsWith("FGU-") }
        .mapNotNull { (name, coord) ->
            val x = coord.x.toFloatOrNull()
            val y = coord.y.toFloatOrNull()
            if (x != null && y != null) name to Offset(y, x) else null
        }

    val showPoints = remember { mutableStateOf(false) }

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
                label = { Text("행 (최대 10)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = columnInput,
                onValueChange = {
                    if (it.length <= 2 && (it.toIntOrNull() ?: 0) <= 10) columnInput = it
                },
                label = { Text("열 (최대 10)") },
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
                "왼쪽 위 기준 (0, 0)",
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
                points = points
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val hasNoCoordinates = viewModel.deviceCoordinates
            .filterKeys { it.startsWith("FGU-") }
            .isEmpty()

        Button(
            onClick = {
                if (hasNoCoordinates) {
                    Toast.makeText(context, "UWB 장비 위치 설정 먼저 해주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    // TODO: 위치 확인 시작 로직 여기에 추가
                }
            },
            modifier = Modifier
                .fillMaxWidth()
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