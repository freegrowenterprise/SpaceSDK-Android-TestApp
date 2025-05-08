package com.growspace.testapp.pages

import androidx.compose.material3.ExposedDropdownMenuBox
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.growspace.sdk.SpaceUwb
import com.growspace.testapp.model.DeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun RangingPage() {
    val context = LocalContext.current as ComponentActivity
    val spaceUWB = remember { SpaceUwb("API-KEY", context, context) }

    val currentMaxConnectCount = remember { mutableIntStateOf(4) }
    val deviceInfoList = remember { mutableStateListOf<DeviceInfo>() }
    val showLoading = remember { mutableStateOf(false) }
    val isScanning = remember { mutableStateOf(false) }
    val isDemoMode = remember { mutableStateOf(false) }
    val showDemoDialog = remember { mutableStateOf(false) }
    val distanceLimit = remember { mutableFloatStateOf(8.0f) }
    val signalPriority = remember { mutableStateOf(true) }
    val notificationTimer = remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun updateDemoDevices() {
        if (deviceInfoList.isEmpty()) {
            repeat(4) { index ->
                deviceInfoList.add(
                    DeviceInfo(
                        name = "DEMO-${1000 + index}",
                        distance = 0.5f + Random.nextFloat() * 7.5f,
                        azimuth = -180f + Random.nextFloat() * 360f,
                        elevation = -90f + Random.nextFloat() * 180f
                    )
                )
            }
        } else {
            val updatedList = deviceInfoList.map {
                it.copy(
                    distance = 0.5f + Random.nextFloat() * 7.5f,
                    azimuth = -180f + Random.nextFloat() * 360f,
                    elevation = -90f + Random.nextFloat() * 180f
                )
            }
            deviceInfoList.clear()
            deviceInfoList.addAll(updatedList)
        }
    }

    fun startUwbScan() {
        deviceInfoList.clear()
        showLoading.value = true
        isScanning.value = true
        isDemoMode.value = false

        spaceUWB.startUwbRanging(
            onUpdate = { result ->
                showLoading.value = false
                val device = DeviceInfo(
                    name = result.deviceName,
                    distance = result.distance,
                    azimuth = result.azimuth,
                    elevation = result.elevation ?: 0f
                )
                val idx = deviceInfoList.indexOfFirst { it.name == device.name }
                if (idx != -1) deviceInfoList[idx] = device else deviceInfoList.add(device)
            },
            onDisconnect = { result ->
                deviceInfoList.removeIf { it.name == result.deviceName }
            }
        )
    }

    fun stopUwbScan() {
        isScanning.value = false
        isDemoMode.value = false
        showLoading.value = false
        spaceUWB.stopUwbRanging {}
        notificationTimer.value?.cancel()
    }

    fun startNotificationTimer() {
        notificationTimer.value?.cancel()
        notificationTimer.value = coroutineScope.launch {
            while (isActive) {
                delay(30000)
                Log.d("NOTIFY", "알림: ${deviceInfoList.size}개 장치 감지됨")
            }
        }
    }

    fun stopNotificationTimer() {
        notificationTimer.value?.cancel()
        notificationTimer.value = null
    }

    LaunchedEffect(showLoading.value, isScanning.value) {
        if (showLoading.value && isScanning.value) {
            delay(5000)
            if (deviceInfoList.isEmpty()) showDemoDialog.value = true
        }
    }

    LaunchedEffect(isDemoMode.value) {
        while (isDemoMode.value) {
            delay(1000)
            updateDemoDevices()
        }
    }

    LaunchedEffect(isScanning.value) {
        if (isScanning.value) startNotificationTimer() else stopNotificationTimer()
    }

    Surface(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Space UWB Scanner",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            MaxConnectionSelector(
                maxConnectCount = currentMaxConnectCount.value,
                onValueChange = { newValue ->
                    currentMaxConnectCount.value = newValue
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최대 연결 거리 설정 (m)", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                OutlinedTextField(
                    value = distanceLimit.value.toString(),
                    onValueChange = {
                        distanceLimit.value = it.toFloatOrNull() ?: distanceLimit.value
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
            Text("RSSI가 큰 장치 우선 연결", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("우선순위 연결", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = signalPriority.value,
                    onCheckedChange = { signalPriority.value = it })
            }
            Spacer(Modifier.height(16.dp))
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                if (showLoading.value) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("장치 검색 중...")
                    }
                } else if (deviceInfoList.isNotEmpty()) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        deviceInfoList.forEach { device ->
                            Card(modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Device: ${device.name}")
                                    Text("거리: ${"%.2f".format(device.distance)}m")
                                    Text("방위각: ${device.azimuth}°, 고도각: ${device.elevation}°")
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("검색된 장치가 없습니다.")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { stopUwbScan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
                Button(
                    onClick = { startUwbScan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start")
                }
            }
        }
    }

    if (showDemoDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showDemoDialog.value = false
                showLoading.value = false
            },
            title = { Text("데모 모드") },
            text = { Text("장치를 찾을 수 없습니다. 데모 모드를 실행하시겠습니까?") },
            confirmButton = {
                Button(onClick = {
                    isDemoMode.value = true
                    showDemoDialog.value = false
                    showLoading.value = false
                }) { Text("확인") }
            },
            dismissButton = {
                Button(onClick = {
                    showDemoDialog.value = false
                    showLoading.value = false
                    isScanning.value = false
                }) { Text("취소") }
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaxConnectionSelector(
    maxConnectCount: Int,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = (1..6).toList()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("최대 연결 개수 설정", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = "$maxConnectCount",
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor()
                    .width(100.dp),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { count ->
                    DropdownMenuItem(
                        text = { Text("$count 개") },
                        onClick = {
                            onValueChange(count)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    Text(
        text = "7개 이상 동시 연결 시 OS 내부적으로 충돌이 발생합니다.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray
    )
}