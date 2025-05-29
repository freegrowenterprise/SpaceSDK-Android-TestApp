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
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.geometry.Offset
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
    val coordinates = remember { mutableStateMapOf<String, Offset>() }
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
        Text("BLE Scan Page")

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { stopScan() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }

            Button(
                onClick = {
                    devices.clear()
                    startScan()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Start BLE Scan")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Discovered Devices: ${devices.size}")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp)
        ) {
            devices.forEach { result ->
                val name = result.device.name ?: "Unknown"
                val coordinate = coordinates[name] ?: Offset(0f, 0f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("name: $name")
                        Text("RSSI: ${result.rssi}")

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            var xText by remember(name) { mutableStateOf(coordinate.x.toString()) }
                            var yText by remember(name) { mutableStateOf(coordinate.y.toString()) }

                            OutlinedTextField(
                                value = xText,
                                onValueChange = {
                                    xText = it
                                    val x = it.toFloatOrNull()
                                    if (x != null) {
                                        coordinates[name] = Offset(x, coordinates[name]?.y ?: 0f)
                                    }
                                },
                                label = { Text("X Coordinates") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = yText,
                                onValueChange = {
                                    yText = it
                                    val y = it.toFloatOrNull()
                                    if (y != null) {
                                        coordinates[name] = Offset(coordinates[name]?.x ?: 0f, y)
                                    }
                                },
                                label = { Text("Y Coordinates") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
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

                Toast.makeText(context, "Save complete", Toast.LENGTH_SHORT).show()
                backDispatcher?.onBackPressed()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}