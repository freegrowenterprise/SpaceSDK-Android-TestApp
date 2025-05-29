package com.growspace.testapp.pages

import androidx.compose.material3.ExposedDropdownMenuBox
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
    val spaceUWB = remember { SpaceUwb(context, context) }

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
    val delayDisconnectSecLimit = remember { mutableIntStateOf(5) }
    val showErrorDialog = remember { mutableStateOf(false) }

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
            },
            maximumConnectionCount = currentMaxConnectCount.intValue,
            replacementDistanceThreshold = distanceLimit.floatValue,
            isConnectStrongestSignalFirst = signalPriority.value,
            delayDisconnectSecLimit = delayDisconnectSecLimit.intValue,
            onResult = {
                result ->
                Log.d("111", "startUwbScan: $result")
                if (!result) {
                    showErrorDialog.value = true
                }
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
            delay(10000)
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

    LaunchedEffect(deviceInfoList.size) {
        if (showDemoDialog.value && deviceInfoList.isNotEmpty()) {
            showDemoDialog.value = false
            isDemoMode.value = false
            showLoading.value = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Device: ${device.name}")
                                    Text("distance: ${"%.2f".format(device.distance)}m")
                                    Text("azimuth: ${device.azimuth}°, elevation: ${device.elevation}°")
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("The device was not detected.")
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
            title = { Text("Run Experience Mode") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("Device connection was not detected. Do you want to run the experience version?\n\n")
                        withStyle(style = SpanStyle(color = Color.Gray)) {
                            append("Don't close the window to continue trying to connect.")
                        }
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    isDemoMode.value = true
                    showDemoDialog.value = false
                    showLoading.value = false
                }) { Text("Ok") }
            },
            dismissButton = {
                Button(onClick = {
                    showDemoDialog.value = false
                    showLoading.value = false
                    isScanning.value = false
                }) { Text("Cancel") }
            }
        )
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

    DisposableEffect(Unit) {
        onDispose {
            stopUwbScan()
        }
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

//addToStrictGattMap() Too many register gatt interface
//onClientRegistered() - status=133 clientIf=0
//BluetoothGatt not initialized or uninitialized characteristic
//UWB ranging notification received for unexpected device address