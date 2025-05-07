package com.growspace.testapp.pages.rtls

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import com.growspace.testapp.model.DeviceCoordinate

class DeviceCoordinateViewModel : ViewModel() {
    // MAC 주소를 키로 하고 x, y 좌표를 저장
    private val _deviceCoordinates = mutableStateMapOf<String, DeviceCoordinate>()
    val deviceCoordinates: Map<String, DeviceCoordinate> = _deviceCoordinates

    fun setCoordinate(macAddress: String, x: String, y: String) {
        _deviceCoordinates[macAddress] = DeviceCoordinate(x, y)
    }
}