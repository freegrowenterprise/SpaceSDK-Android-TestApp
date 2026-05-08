package com.growspace.testapp.pages

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.growspace.sdk.SpaceUwb
import com.growspace.testapp.model.DeviceInfo

class RangingSessionViewModel : ViewModel() {
    val currentMaxConnectCount = mutableIntStateOf(4)
    val deviceInfoList = mutableStateListOf<DeviceInfo>()
    val showLoading = mutableStateOf(false)
    val isScanning = mutableStateOf(false)
    val isDemoMode = mutableStateOf(false)
    val distanceLimit = mutableFloatStateOf(80.0f)
    val signalPriority = mutableStateOf(true)
    val delayDisconnectSecLimit = mutableIntStateOf(5)
    val showErrorDialog = mutableStateOf(false)
    val isButtonLoading = mutableStateOf(false)
    val isAzimuthSupported = mutableStateOf<Boolean?>(null)
    val isDistanceSupported = mutableStateOf<Boolean?>(null)
    val isElevationSupported = mutableStateOf<Boolean?>(null)
    val sessionDisconnectedDeviceNames = mutableStateListOf<String>()

    private var spaceUwb: SpaceUwb? = null

    fun getSpaceUwb(context: Context, activity: Activity): SpaceUwb {
        return spaceUwb ?: SpaceUwb(context.applicationContext, activity).also {
            spaceUwb = it
        }
    }

    fun markSessionDisconnected(deviceName: String) {
        if (deviceName.isEmpty()) return
        if (!sessionDisconnectedDeviceNames.contains(deviceName)) {
            sessionDisconnectedDeviceNames.add(deviceName)
        }
    }

    fun isSessionDisconnected(deviceName: String): Boolean =
        sessionDisconnectedDeviceNames.contains(deviceName)

    override fun onCleared() {
        spaceUwb?.stopUwbRanging(
            delayDisconnectSecLimit = delayDisconnectSecLimit.intValue
        )
        spaceUwb = null
        super.onCleared()
    }
}
