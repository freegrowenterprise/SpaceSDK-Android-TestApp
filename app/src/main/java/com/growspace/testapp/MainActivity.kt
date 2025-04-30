package com.growspace.testapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.growspace.sdk.SpaceUwb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import com.growspace.testapp.model.DeviceInfo
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay
import kotlin.random.Random

data class RssiTime(val rssi: Int, val time: Long)

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val scanner: BluetoothLeScanner? by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private val discoveredDevices = ConcurrentHashMap<String, RssiTime>()
    private var scanCallback: ScanCallback? = null
    private var previousBestMacAddress: String? = null
    private var previousBestRssi: Int = Int.MIN_VALUE
    private var lastBestChangeTime: Long = 0
    private var devicesInfoList = mutableStateListOf<DeviceInfo>()
    private var showLoading = mutableStateOf(false)
    private var isScanning = mutableStateOf(false)
    private var isDemoMode = mutableStateOf(false)
    private var showDemoDialog = mutableStateOf(false)

    private val apiKey = "API-KEY"
    private lateinit var spaceUWB: SpaceUwb

    private val CustomColorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        secondary = Color.Gray,
        onSecondary = Color.White
    )

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        spaceUWB = SpaceUwb(apiKey, this, this)
        CompanionActivityHolder.activity = this

        setContent {
            // UI에 필요한 상태들 remember로 선언
            val distanceLimit = remember { mutableFloatStateOf(4.0f) }
            val signalPriority = remember { mutableStateOf(true) }

            MaterialTheme(
                colorScheme = CustomColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        maxConnectCount = 4,
                        distanceLimit = distanceLimit.value,
                        onDistanceChange = {
                            distanceLimit.value = it.toFloatOrNull() ?: distanceLimit.value
                        },
                        signalPriority = signalPriority.value,
                        onSignalPriorityToggle = { signalPriority.value = it },
                        deviceInfoList = devicesInfoList,
                        showLoading = showLoading,
                        isScanning = isScanning,
                        isDemoMode = isDemoMode,
                        showDemoDialog = showDemoDialog,
                        onStartScan = {
                            devicesInfoList.clear()  // 스캔 시작 시 리스트 초기화
                            showLoading.value = true
                            isScanning.value = true
                            isDemoMode.value = false
                            spaceSDKStartUwbRanging()
                        },
                        onStopScan = {
                            spaceSDKStopUwbRanging()
                            showLoading.value = false
                            isDemoMode.value = false
                            isScanning.value = false
                        },
                        updateDemoDevices = { updateDemoDevices(currentMaxConnectCount = 4) },
                        clearDevices = { devicesInfoList.clear() }
                    )
                }
            }
        }
    }

    @Composable
    fun MainScreen(
        maxConnectCount: Int,
        onStartScan: () -> Unit,
        onStopScan: () -> Unit,
        distanceLimit: Float,
        onDistanceChange: (String) -> Unit,
        signalPriority: Boolean,
        onSignalPriorityToggle: (Boolean) -> Unit,
        deviceInfoList: List<DeviceInfo>,
        showLoading: MutableState<Boolean>,
        isDemoMode: MutableState<Boolean>,
        isScanning: MutableState<Boolean>,
        showDemoDialog: MutableState<Boolean>,
        updateDemoDevices: () -> Unit,
        clearDevices: () -> Unit
    ) {
//        var statusText by remember { mutableStateOf("") }
        var currentMaxConnectCount by remember { mutableStateOf(maxConnectCount) }
        val coroutineScope = rememberCoroutineScope()

        // 데모 모드 타이머 효과
        LaunchedEffect(showLoading.value, isScanning.value) {
            if (showLoading.value && isScanning.value) {
                delay(5000)
                if (deviceInfoList.isEmpty()) {
                    showDemoDialog.value = true
                }
            }
        }

        // 데모 모드 실행
        LaunchedEffect(isDemoMode.value) {
            while (isDemoMode.value) {
                delay(1000)
                updateDemoDevices()
            }
        }

        // 데모 모드 다이얼로그
        if (showDemoDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showDemoDialog.value = false
                    showLoading.value = false
                },
                title = { Text("데모 모드") },
                text = { Text("장치를 찾을 수 없습니다. 데모 모드를 실행하시겠습니까?") },
                confirmButton = {
                    Button(
                        onClick = {
                            isDemoMode.value = true
                            showDemoDialog.value = false
                            showLoading.value = false
                        }
                    ) {
                        Text("확인")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDemoDialog.value = false
                            showLoading.value = false
                            isScanning.value = false
                        }
                    ) {
                        Text("취소")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 상단 제목
            Text(
                text = "Space UWB Scanner",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 최대 연결 개수 설정
            MaxConnectionSelector(
                maxConnectCount = currentMaxConnectCount,
                onValueChange = { newValue ->
                    currentMaxConnectCount = newValue
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 거리 설정
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("최대 연결 거리 설정 (m)", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                OutlinedTextField(
                    value = distanceLimit.toString(),
                    onValueChange = onDistanceChange,
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
            Text(
                text = "설정 거리에서 초과되었을 때 연결을 끊고 새로운 장치 연결을 시도합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 스위치 설정
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("신호 강한 순 우선 연결 설정", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = signalPriority,
                    onCheckedChange = onSignalPriorityToggle
                )
            }
            Text(
                text = "RSSI가 가장 큰 UWB 장치부터 연결을 시도합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 디바이스 정보 카드들 (스크롤 가능한 영역)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLoading.value) {
                    // 로딩 UI
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("장치 검색 중...")
                        }
                    }
                } else if (deviceInfoList.isNotEmpty()) {
                    // 장치 리스트
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        deviceInfoList.forEach { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F7))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Device Name: ${device.name}")
                                    Text("Distance: ${"%.2f".format(device.distance)}m")
                                    Text("Azimuth: ${device.azimuth}°")
                                    Text("Elevation: ${device.elevation}°")
                                }
                            }
                        }
                    }
                } else {
                    // 장치가 없을 때 표시할 UI
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("검색된 장치가 없습니다.")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 스캔 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onStopScan()
                        showLoading.value = false
                        isDemoMode.value = false
                        isScanning.value = false
//                        clearDevices()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop UWB Scan")
                }

                Button(
                    onClick = {
                        clearDevices()
                        showLoading.value = true
                        isScanning.value = true
                        isDemoMode.value = false
                        onStartScan()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start UWB Scan")
                }
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

    private fun updateDemoDevices(currentMaxConnectCount: Int) {
        if (devicesInfoList.isEmpty()) {
            repeat(currentMaxConnectCount) { index ->
                devicesInfoList.add(
                    DeviceInfo(
                        name = "DEMO-${1000 + index}",
                        distance = 0.5f + Random.nextFloat() * 7.5f,
                        azimuth = -180f + Random.nextFloat() * 360f,
                        elevation = -90f + Random.nextFloat() * 180f
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBLEScan() {
        if (!hasBluetoothPermission()) {
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val macAddress = device.address
                val rssi = result.rssi.toLong()
                val serviceData = result.scanRecord?.serviceData
                val currentTime = System.currentTimeMillis()

                if (!serviceData.isNullOrEmpty()) {
                    for ((uuid, data) in serviceData) {
                        val uuidString = uuid.toString().lowercase()

                        if (uuidString.contains("ffe1") && rssi > -76) {
                            discoveredDevices.entries.removeIf { (_, time) -> currentTime - time.time > 3000 }
                            discoveredDevices[macAddress] = RssiTime(rssi.toInt(), currentTime)

                            val bestBle = discoveredDevices.maxByOrNull { it.value.rssi }?.key
                            val bestRssi = discoveredDevices[bestBle]

                            if (bestBle != null && bestRssi != null) {
                                if (bestBle != previousBestMacAddress) {
                                    if (currentTime - lastBestChangeTime >= 1000) {
                                        previousBestMacAddress = bestBle
                                        previousBestRssi = bestRssi.rssi
                                        lastBestChangeTime = currentTime

                                        Log.e("MMMIIIN", "API 조회!!")
                                        sendBeaconData(bestBle) { result ->
                                            result.onSuccess { response ->
                                                Log.e("MMMIIIN", "✅ 내부 서버 응답: ${response.zoneName}")
                                            }.onFailure { error ->
                                                Log.e("MMMIIIN", "❌ API 요청 실패: ${error.message}")
                                            }
                                        }
                                    }
                                } else {
                                    lastBestChangeTime = currentTime
                                }
                            }
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                println("❌ 스캔 실패: 에러 코드 $errorCode")
            }
        }

        scanner?.startScan(scanCallback)
    }

    private fun stopBLEScan() {
        if (!hasBluetoothPermission()) {
            return
        }

        try {
            scanCallback?.let {
                scanner?.stopScan(it)
                Log.d("MMMIIIN", "✅ BLE 스캔 중지됨")
                scanCallback = null
            }
        } catch (e: SecurityException) {
            Log.e("MMMIIIN", "❌ BLE 스캔 중지 실패: ${e.localizedMessage}")
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.UWB_RANGING
        )

        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    fun sendBeaconData(macAddress: String, onResponse: (Result<SpaceLocation>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.sendBeaconData(macAddress) { spaceLocation ->
                    if (spaceLocation != null) {
                        Log.e(
                            "MMMIIIN",
                            "✅ 서버 응답: ${spaceLocation.zoneName}, X: ${spaceLocation.locationX}, Y: ${spaceLocation.locationY}"
                        )
                    } else {
                        Log.e("MMMIIIN", "❌ 응답 데이터 없음")
                    }
                }
            } catch (e: Exception) {
                Log.e("MMMIIIN", "❌ 통신 API 요청 실패: ${e.localizedMessage}")
                onResponse(Result.failure(e))
            }
        }
    }

    private fun spaceSDKStartUwbRanging() {
        spaceUWB.startUwbRanging(
            onUpdate = { result ->
                val deviceId = result.deviceName
                showLoading.value = false  // 첫 번째 장치가 발견되면 로딩 UI 숨김

                val deviceInfo = DeviceInfo(
                    name = deviceId,
                    distance = result.distance,
                    azimuth = result.azimuth,
                    elevation = result.elevation ?: 0f
                )

                runOnUiThread {
                    val existingIndex = devicesInfoList.indexOfFirst { it.name == deviceId }
                    if (existingIndex != -1) {
                        devicesInfoList[existingIndex] = deviceInfo
                    } else {
                        devicesInfoList.add(deviceInfo)
                    }
                    Log.d("UWB_DEBUG", "Device updated: $deviceId, Total devices: ${devicesInfoList.size}")
                }
            },
            onDisconnect = { result ->
                val deviceId = result.deviceName
                runOnUiThread {
                    devicesInfoList.removeIf { it.name == deviceId }
                    Log.d("UWB_DEBUG", "Device disconnected: $deviceId, Remaining devices: ${devicesInfoList.size}")
                }
            }
        )
    }

    private fun spaceSDKStopUwbRanging() {
        spaceUWB.stopUwbRanging(
            onComplete = { result ->
                if (result.isFailure) {
                    Log.e("MMMIIIN", "❌ UWB 작업 중지 실패: ${result.exceptionOrNull()}")
                } else {
                    Log.d("MMMIIIN", "✅ UWB 작업 중지 성공")
                }
            }
        )
    }

    private fun logDownload() {
        spaceUWB.exportLogsTxt()
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}