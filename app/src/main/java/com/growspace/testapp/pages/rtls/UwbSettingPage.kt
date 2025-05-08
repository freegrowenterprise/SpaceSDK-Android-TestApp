package com.growspace.testapp.pages

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.growspace.testapp.model.DeviceCoordinate
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.growspace.testapp.pages.rtls.DeviceCoordinateViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressLint("MissingPermission")
@Composable
fun UwbSettingPage(viewModel: DeviceCoordinateViewModel) {
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val scanner = bluetoothAdapter.bluetoothLeScanner

    val devices = remember { mutableStateListOf<ScanResult>() }
    val coordinates = remember { mutableStateMapOf<String, DeviceCoordinate>() }
    var isScanning by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_SCAN] == true) {
            Log.d("BLE", "권한 승인됨")
        }
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let {
                    val deviceName = it.device.name ?: return
                    if (deviceName.startsWith("FGU-") &&
                        devices.none { d -> d.device.address == it.device.address }) {
                        devices.add(it)
                        viewModel.deviceCoordinates[deviceName]?.let { coord ->
                            coordinates[deviceName] = coord
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "스캔 실패: $errorCode")
            }
        }
    }

    fun stopScan() {
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    fun startScan() {
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(null, settings, scanCallback)
        isScanning = true

        CoroutineScope(Dispatchers.Main).launch {
            delay(10_000) // 10,000ms = 10초
            stopScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("BLE 스캔 페이지")

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { stopScan() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.weight(1f)
            ) {
                Text("스캔 중지")
            }

            Button(
                onClick = {
                    devices.clear()
                    startScan()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("스캔 시작")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("발견된 장치: ${devices.size}개")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        ) {
            devices.forEach { result ->
                val name = result.device.name ?: "Unknown"
                val coordinate = coordinates[name] ?: DeviceCoordinate("", "")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("이름: $name")
                        Text("RSSI: ${result.rssi}")

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = coordinate.x,
                                onValueChange = { coordinates[name] = coordinate.copy(x = it) },
                                label = { Text("X 좌표") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = coordinate.y,
                                onValueChange = { coordinates[name] = coordinate.copy(y = it) },
                                label = { Text("Y 좌표") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                coordinates.forEach { (name, coord) ->
                    viewModel.setCoordinate(name, coord.x, coord.y)
                    Log.d("SAVE", "[$name] -> X=${coord.x}, Y=${coord.y}")
                }

                Toast.makeText(context, "저장 완료", Toast.LENGTH_SHORT).show()
                backDispatcher?.onBackPressed()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("저장")
        }
    }
}