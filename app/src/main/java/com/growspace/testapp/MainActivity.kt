package com.growspace.testapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.growspace.sdk.SpaceUwb
//import com.growspace.sdk.GrowSpaceSDK
//import com.growspace.sdk.model.ScanRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

data class RssiTime(val rssi: Int, val time: Long)

class MainActivity : ComponentActivity() {

    private lateinit var startScanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var logButton: Button

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val scanner: BluetoothLeScanner? by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private val discoveredDevices = ConcurrentHashMap<String, RssiTime>() // ✅ 발견된 BLE 목록 (시간 저장)
    private val handler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null
    private var previousBestMacAddress: String? = null
    private var previousBestRssi: Int = Int.MIN_VALUE
    private var lastBestChangeTime: Long = 0

    private val apiKey = "553f1709-a245-404f-a02a-d3bc4861be43";

    //    private lateinit var growSpaceSDK: GrowSpaceSDK
    private lateinit var spaceUWB: SpaceUwb

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissions()

        spaceUWB = SpaceUwb(apiKey, this, this)

        startScanButton = findViewById(R.id.startScanButton)
        stopScanButton = findViewById(R.id.stopScanButton)
        statusTextView = findViewById(R.id.statusTextView)
        logButton = findViewById(R.id.logButton)

        logButton.setOnClickListener {
            logDownload()
        }

        startScanButton.setOnClickListener {
//            startBLEScan()
//            spaceSDKStartScan()
            spaceSDKStartUwbRanging()
            statusTextView.text = "✅ 스캔 시작됨"
        }

        stopScanButton.setOnClickListener {
//            stopBLEScan()
            spaceSDKStopUwbRanging()
            statusTextView.text = "❌ 스캔 중지됨"
//            sendBeaconData(macAddress = "c3000029d2a5") { response ->
//                if (response != null) {
//                    println("API 통신 결과 : $response")
//                } else {
//                    println("API 통신 실패")
//                }
//            }
        }

        CompanionActivityHolder.activity = this
//        requestIgnoreBatteryOptimizations()

//        growSpaceSDK = GrowSpaceSDK(apiKey, this)
    }

    @SuppressLint("MissingPermission")
    private fun startBLEScan() {
        if (!hasBluetoothPermission()) {
            statusTextView.text = "❌ 블루투스 권한이 필요합니다."
            return
        }

        statusTextView.text = "🔍 BLE 스캔 시작 중..."

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val macAddress = device.address
                val rssi = result.rssi.toLong()
                val serviceData = result.scanRecord?.serviceData
                val currentTime = System.currentTimeMillis()

                // ✅ 특정 UUID("ffe1")를 가진 BLE만 필터링
                if (!serviceData.isNullOrEmpty()) {
                    for ((uuid, data) in serviceData) {
                        val uuidString = uuid.toString().lowercase()

                        if (uuidString.contains("ffe1") && rssi > -76) {
                            // ✅ 3초 이상 지난 BLE 제거
                            discoveredDevices.entries.removeIf { (_, time) -> currentTime - time.time > 3000 }

                            // ✅ RSSI 업데이트 (가장 최근 값으로 갱신)
                            discoveredDevices[macAddress] = RssiTime(rssi.toInt(), currentTime)

                            // ✅ RSSI가 가장 높은 BLE 찾기
                            val bestBle = discoveredDevices.maxByOrNull { it.value.rssi }?.key
                            val bestRssi = discoveredDevices[bestBle]

                            // ✅ 현재 BLE가 바뀌었을 경우
                            if (bestBle != null && bestRssi != null) {
                                if (bestBle != previousBestMacAddress) {
                                    // ✅ 변경된 BLE가 최소 1초 동안 유지되어야 감지
                                    if (currentTime - lastBestChangeTime >= 1000) {
                                        previousBestMacAddress = bestBle
                                        previousBestRssi = bestRssi.rssi
                                        lastBestChangeTime = currentTime

                                        Log.e("MMMIIIN", "API 조회!!")
                                        // ✅ API 호출 및 에러 핸들링 추가
                                        sendBeaconData(bestBle) { result ->
                                            result.onSuccess { response ->
                                                Log.e("MMMIIIN", "✅ 내부 서버 응답: ${response.zoneName}")
                                            }.onFailure { error ->
                                                Log.e("MMMIIIN", "❌ API 요청 실패: ${error.message}")
                                            }
                                        }
                                    }
                                } else {
                                    lastBestChangeTime = currentTime // BLE가 유지되면 시간 갱신
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
        statusTextView.text = "✅ BLE 스캔 시작됨"
    }

    private fun stopBLEScan() {
        if (!hasBluetoothPermission()) {
            statusTextView.text = "❌ 블루투스 권한이 필요합니다."
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
//                val response = ApiClient.sendBeaconData(BeaconRequest(macAddress))
//                Log.e("MMMIIIN", "✅ 통신 서버 응답: ${response.zoneName}")
//                onResponse(Result.success(response))
                ApiClient.sendBeaconData(macAddress) { spaceLocation ->
                    if (spaceLocation != null) {
                        Log.e(
                            "MMMIIIN",
                            "✅ 서버 응답: ${spaceLocation.zoneName}, X: ${spaceLocation.locationX}, Y: ${spaceLocation.locationY}"
                        )
//                        println("✅ 서버 응답: ${spaceLocation.zoneName}, X: ${spaceLocation.locationX}, Y: ${spaceLocation.locationY}")
                    } else {
                        Log.e("MMMIIIN", "❌ 응답 데이터 없음")
                    }
                }
            } catch (e: Exception) {
                Log.e("MMMIIIN", "❌ 통신 API 요청 실패: ${e.localizedMessage}")
                onResponse(Result.failure(e)) // ✅ 에러 발생 시 실패 전달
            }
        }
    }

    //    private fun spaceSDKStartScan() {
//        val growSpaceSDK = GrowSpaceSDK(apiKey, this)
//        growSpaceSDK.startScanning(ScanRate.MEDIUM) { result ->
//            result.onSuccess { response ->
//                Log.e("MMMIIIN", "✅ SDK 서버 응답: ${response}")
//            }.onFailure { error ->
//                Log.e("MMMIIIN", "❌ SDK 요청 실패: ${error.localizedMessage}")
//            }
//        }
//    }
//
//    private fun spaceSDKStopScan() {
//        val growSpaceSDK = GrowSpaceSDK("API_KEY", this)
//        growSpaceSDK.stopScanning()
//    }
//
    private val deviceInfoMap = mutableMapOf<String, String>()  // key = device ID, value = 표시 텍스트

    private fun spaceSDKStartUwbRanging() {
        spaceUWB.startUwbRanging(
            onUpdate = { result ->
                runOnUiThread {
                    val deviceId = result.deviceName

                    val newText = """
                    ✅ UWB 장치 발견
                    ID: $deviceId
                    거리: ${result.distance} m
                    방위각: ${result.azimuth}°
                    고도각: ${result.elevation}°
                """.trimIndent()

                    // 👇 동일한 기기가 이미 있다면 덮어쓰기
                    deviceInfoMap[deviceId] = newText

                    // 👇 전체 Map을 합쳐서 화면 표시
                    statusTextView.text = deviceInfoMap.values.joinToString(separator = "\n\n")
                }
            },
            onDisconnect = { result ->
                runOnUiThread {
                    val deviceId = result.deviceName
                    deviceInfoMap.remove(deviceId)

                    statusTextView.text = deviceInfoMap.values.joinToString(separator = "\n\n")
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
        spaceUWB.exportLogsTxt();
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