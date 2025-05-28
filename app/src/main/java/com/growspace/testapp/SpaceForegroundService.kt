package com.growspace.testapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.growspace.sdk.SpaceUwb

class SpaceForegroundService: Service() {
    private val CHANNEL_ID = "ForegroundServiceChannel"
    private lateinit var spaceUWB: SpaceUwb
    private var activity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        // 필요한 초기화 작업
    }

//    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activity = CompanionActivityHolder.activity
        if (activity != null) {
            spaceUWB = SpaceUwb(this, activity!!)
        } else {
            Log.e("SpaceForegroundService", "Activity is null, SpaceUwb 초기화 실패")
        }
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("서비스 동작 중")
            .setContentText("백그라운드에서도 함수를 실행합니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )

//    Handler(Looper.getMainLooper()).postDelayed({
//        spaceUWB.startUwbRanging(
//            onUpdate = { result ->
//                Log.e(
//                    "MMMIIIN",
//                    "✅ 백그라운드 UWB 장치 발견: ${result.deviceName} ${result.distance} ${result.azimuth} ${result.elevation}"
//                )
//            },
//            onDisconnect = { result ->
//                Log.e(
//                    "MMMIIIN",
//                    "❌ 백그라운드 UWB 장치 연결 끊김: ${result.deviceName} ${result.disConnectType}"
//                )
//            }
//        )
//        Log.d("MMMIIIN", "✅ 3초 대기 후 UWB 스캔 시작")
//    }, 3000) // 3000밀리초 = 3초

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}