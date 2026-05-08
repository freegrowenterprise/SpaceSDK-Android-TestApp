package com.growspace.testapp.pages

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.growspace.sdk.model.DisconnectType
import com.growspace.testapp.model.DeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/// 사유에 따라 색깔 분기되는 Snackbar visuals
private data class ColoredSnackbarVisuals(
    override val message: String,
    val containerColor: Color,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RangingPage(navController: NavHostController? = null) {
    val context = LocalContext.current as ComponentActivity
    val sessionViewModel: RangingSessionViewModel = viewModel()
    val spaceUWB = sessionViewModel.getSpaceUwb(context, context)
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val currentMaxConnectCount = sessionViewModel.currentMaxConnectCount
    val deviceInfoList = sessionViewModel.deviceInfoList
    val showLoading = sessionViewModel.showLoading
    val isScanning = sessionViewModel.isScanning
    val isDemoMode = sessionViewModel.isDemoMode
    val distanceLimit = sessionViewModel.distanceLimit
    val signalPriority = sessionViewModel.signalPriority
    val notificationTimer = remember { mutableStateOf<Job?>(null) }
    val delayDisconnectSecLimit = sessionViewModel.delayDisconnectSecLimit
    val showErrorDialog = sessionViewModel.showErrorDialog
    val isButtonLoading = sessionViewModel.isButtonLoading
    val isAzimuthSupported = sessionViewModel.isAzimuthSupported
    val isDistanceSupported = sessionViewModel.isDistanceSupported
    val isElevationSupported = sessionViewModel.isElevationSupported

    LaunchedEffect(Unit) {
        if (isAzimuthSupported.value != null) return@LaunchedEffect
        try {
            spaceUWB.checkAzimuthElevationSupport { azimuth, distance, elevation, _, _, _ ->
                isAzimuthSupported.value = azimuth
                isDistanceSupported.value = distance
                isElevationSupported.value = elevation
            }
        } catch (e: Exception) {
            Log.w("RangingPage", "checkAzimuthElevationSupport failed", e)
        }
    }

    // 카드 long-press → BottomSheet 대상
    val deviceActionTarget = remember { mutableStateOf<String?>(null) }

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

    fun showDisconnectSnackbar(name: String, type: DisconnectType) {
        val (msg, color) = when (type) {
            DisconnectType.DISCONNECTED_DUE_TO_DISTANCE ->
                "$name — 거리 초과로 연결 해제" to Color(0xFF1976D2)
            DisconnectType.DISCONNECTED_DUE_TO_SYSTEM ->
                "$name — 시스템 종료로 연결 해제" to Color(0xFF616161)
            DisconnectType.DISCONNECTED_DUE_TO_TIMEOUT ->
                "$name — 응답 끊김 (timeout)" to Color(0xFFC62828)
            DisconnectType.DISCONNECTED_DUE_TO_NO_DATA ->
                "$name — UWB 데이터 미수신 (회귀 가능성)" to Color(0xFFE65100)
        }
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                ColoredSnackbarVisuals(message = msg, containerColor = color)
            )
        }
    }

    fun startUwbScan() {
        isButtonLoading.value = true

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(context, "This device does not support Bluetooth.", Toast.LENGTH_SHORT).show()
            isButtonLoading.value = false
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                isButtonLoading.value = false
                return
            }
            context.startActivity(enableBtIntent)
            isButtonLoading.value = false
            return
        }

        deviceInfoList.clear()
        showLoading.value = true
        isScanning.value = true
        isDemoMode.value = false

        sessionViewModel.sessionDisconnectedDeviceNames.forEach { disconnectedName ->
            spaceUWB.disconnectDevice(disconnectedName)
        }

        spaceUWB.startUwbRanging(
            onUpdate = onUpdate@{ result ->
                showLoading.value = false
                isButtonLoading.value = false
                if (sessionViewModel.isSessionDisconnected(result.deviceName)) {
                    deviceInfoList.removeIf { it.name == result.deviceName }
                    spaceUWB.disconnectDevice(result.deviceName)
                    return@onUpdate
                }
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
                showDisconnectSnackbar(result.deviceName, result.disConnectType)
            },
            maximumConnectionCount = currentMaxConnectCount.intValue,
            replacementDistanceThreshold = distanceLimit.floatValue,
            isConnectStrongestSignalFirst = signalPriority.value,
            delayDisconnectSecLimit = delayDisconnectSecLimit.intValue,
            onResult = { result ->
                isButtonLoading.value = false
                if (!result) {
                    showErrorDialog.value = true
                }
            }
        )
    }

    fun stopUwbScan() {
        isButtonLoading.value = true
        isScanning.value = false
        isDemoMode.value = false
        showLoading.value = false
        spaceUWB.stopUwbRanging(
            onComplete = { _ ->
                isButtonLoading.value = false
            },
            delayDisconnectSecLimit = delayDisconnectSecLimit.intValue
        )
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

    LaunchedEffect(isDemoMode.value) {
        while (isDemoMode.value) {
            delay(1000)
            updateDemoDevices()
        }
    }

    LaunchedEffect(isScanning.value) {
        if (isScanning.value) startNotificationTimer() else stopNotificationTimer()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                val containerColor = (data.visuals as? ColoredSnackbarVisuals)?.containerColor
                    ?: SnackbarDefaults.color
                Snackbar(
                    snackbarData = data,
                    containerColor = containerColor,
                    contentColor = Color.White
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Space UWB Scanner") },
                actions = {
                    if (navController != null) {
                        IconButton(onClick = { navController.navigate("blocklist") }) {
                            Icon(Icons.Default.Block, contentDescription = "BlockList")
                        }
                        IconButton(onClick = { navController.navigate("check") }) {
                            Icon(Icons.Default.Info, contentDescription = "Capability info")
                        }
                    }
                }
            )
        }
    ) { paddings ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddings)
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CapabilityBadge(
                    isAzimuthSupported = isAzimuthSupported.value,
                    isDistanceSupported = isDistanceSupported.value,
                    isElevationSupported = isElevationSupported.value
                )

                Spacer(Modifier.height(16.dp))

                MaxConnectionSelector(
                    maxConnectCount = currentMaxConnectCount.value,
                    onValueChange = { newValue ->
                        currentMaxConnectCount.value = newValue
                    }
                )

                Spacer(Modifier.height(16.dp))

                DelayInputField(delayDisconnectSecLimit)

                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "maximum connection distance (m)",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    OutlinedTextField(
                        value = distanceLimit.value.toString(),
                        onValueChange = {
                            distanceLimit.value = it.toFloatOrNull() ?: distanceLimit.value
                        },
                        modifier = Modifier.width(100.dp),
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RSSI Priority Connection Settings", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = signalPriority.value,
                        onCheckedChange = { signalPriority.value = it })
                }
                Text(
                    text = "Attempt to connect UWB devices with the largest RSSI sequentially.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (showLoading.value) {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Searching for devices...")
                        }
                    } else if (deviceInfoList.isNotEmpty()) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            deviceInfoList.forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { /* no-op */ },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                deviceActionTarget.value = device.name
                                            }
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Device: ${device.name}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text("distance: ${"%.2f".format(device.distance)}m")
                                        Text("azimuth: ${device.azimuth}°, elevation: ${device.elevation}°")
                                        Text(
                                            text = "길게 눌러 단일 끊기 / 차단",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("The device was not detected.", color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            stopUwbScan()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isButtonLoading.value && isScanning.value
                    ) {
                        if (isButtonLoading.value && !isScanning.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Stop")
                        }
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            startUwbScan()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isButtonLoading.value && !isScanning.value
                    ) {
                        if (isButtonLoading.value && isScanning.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }

    // 디바이스 long-press → ModalBottomSheet (Material3 표준 패턴)
    val target = deviceActionTarget.value
    if (target != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { deviceActionTarget.value = null },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = target,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "디바이스 액션을 선택하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                ListItem(
                    headlineContent = { Text("이 디바이스만 끊기") },
                    supportingContent = {
                        Text(
                            "현재 페이지 세션 동안 재연결 시 계속 끊음",
                            color = Color.Gray
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.LinkOff, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        sessionViewModel.markSessionDisconnected(target)
                        deviceInfoList.removeIf { it.name == target }
                        spaceUWB.disconnectDevice(target)
                        deviceActionTarget.value = null
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            text = "이 디바이스 차단 (영구)",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text(
                            "unblock 호출 전까지 자동 재연결 차단",
                            color = Color.Gray
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        spaceUWB.blockDevice(target)
                        deviceActionTarget.value = null
                    }
                )

                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { deviceActionTarget.value = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("취소") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showErrorDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog.value = false
            },
            title = {
                Text("Connection failed")
            },
            text = {
                Text("UWB device connection failed.\nPlease try again.")
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog.value = false }) {
                    Text("Ok")
                }
            }
        )
    }
}

@Composable
private fun CapabilityBadge(
    isAzimuthSupported: Boolean?,
    isDistanceSupported: Boolean?,
    isElevationSupported: Boolean?,
) {
    val text: String
    val container: Color
    val onContainer: Color

    when {
        isAzimuthSupported == null -> {
            text = "이 폰의 UWB capability 조회 중…"
            container = Color(0xFFEFEFEF)
            onContainer = Color.DarkGray
        }
        isAzimuthSupported == true -> {
            text = "이 폰: direction OK (azimuth=true, distance=$isDistanceSupported, elevation=$isElevationSupported)"
            container = Color(0xFFE7F5EC)
            onContainer = Color(0xFF1B5E20)
        }
        else -> {
            text = "이 폰: direction 미지원 (distance only)"
            container = Color(0xFFFFF3E0)
            onContainer = Color(0xFFE65100)
        }
    }

    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
        Text("maximum connections", style = MaterialTheme.typography.bodyLarge)
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
                        text = { Text("$count") },
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
        text = "When more than seven concurrent connections occur, the OS internally collides.",
        style = MaterialTheme.typography.bodySmall,
        color = Color.Gray
    )
}

@Composable
fun DelayInputField(delayDisconnectSecLimit: MutableState<Int>) {
    val min = 3
    val max = 10
    val inputText = remember { mutableStateOf(delayDisconnectSecLimit.value.toString()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Set automatic deletion time in case of delay (S)",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Column {
            IconButton(
                onClick = {
                    val newValue = (delayDisconnectSecLimit.value + 1).coerceAtMost(max)
                    delayDisconnectSecLimit.value = newValue
                    inputText.value = newValue.toString()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase")
            }

            IconButton(
                onClick = {
                    val newValue = (delayDisconnectSecLimit.value - 1).coerceAtLeast(min)
                    delayDisconnectSecLimit.value = newValue
                    inputText.value = newValue.toString()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease")
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        OutlinedTextField(
            value = inputText.value,
            onValueChange = { newText ->
                inputText.value = newText

                val number = newText.toIntOrNull()
                if (number != null) {
                    val corrected = number.coerceIn(min, max)

                    delayDisconnectSecLimit.value = corrected

                    if (corrected.toString() != newText) {
                        inputText.value = corrected.toString()
                    }
                }
            },
            modifier = Modifier.width(100.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )
    }
}
