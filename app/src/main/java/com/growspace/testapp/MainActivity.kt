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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.activity.viewModels
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.growspace.testapp.pages.AppNavHost
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

data class RssiTime(val rssi: Int, val time: Long)

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private var devicesInfoList = mutableStateListOf<DeviceInfo>()
    private var showLoading = mutableStateOf(false)

    private val apiKey = "API-KEY"
    private lateinit var spaceUWB: SpaceUwb

    private val CustomColorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        surfaceVariant = Color(0xFFF2F2F2),
        onSurface = Color.Black,
        secondary = Color.Gray,
        onSecondary = Color.White
    )

    private val CHANNEL_ID = "device_list_channel"
    private val NOTIFICATION_ID = 1
    private var notificationTimer: Job? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestPermissions()
        spaceUWB = SpaceUwb(apiKey, this, this)
        CompanionActivityHolder.activity = this

        setContent {
            MaterialTheme(
                colorScheme = CustomColorScheme
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }


    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.UWB_RANGING,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (permissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 1)
        }
    }

    private fun spaceSDKStartUwbRanging() {
        spaceUWB.startUwbRanging(
            onUpdate = { result ->
                val deviceId = result.deviceName
                showLoading.value = false

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Device List Updates"
            val descriptionText = "Shows updates about connected devices"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showDeviceListNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val deviceListText = if (devicesInfoList.isEmpty()) {
            "연결된 장치가 없습니다."
        } else {
            devicesInfoList.joinToString("\n") { device ->
                "${device.name}: ${"%.2f".format(device.distance)}m"
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("연결된 장치 목록")
            .setContentText("${devicesInfoList.size}개의 장치가 연결되어 있습니다.")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(deviceListText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d("Notification", "알림이 표시되었습니다.")
            } else {
                Log.e("Notification", "알림 권한이 없습니다.")
            }
        } catch (e: Exception) {
            Log.e("Notification", "알림 표시 중 오류 발생: ${e.message}")
        }
    }

    private fun startNotificationTimer() {
        notificationTimer?.cancel()
        notificationTimer = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                showDeviceListNotification()
                delay(30000)
            }
        }
    }

    private fun stopNotificationTimer() {
        notificationTimer?.cancel()
        notificationTimer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNotificationTimer()
    }
}