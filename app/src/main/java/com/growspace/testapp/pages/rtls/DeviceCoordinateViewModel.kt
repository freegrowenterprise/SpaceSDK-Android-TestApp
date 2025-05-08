package com.growspace.testapp.pages.rtls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
//import com.growspace.testapp.model.DeviceCoordinate

class DeviceCoordinateViewModel : ViewModel() {
    // MAC 주소를 키로 하고 x, y 좌표를 저장
    private val _deviceCoordinates = mutableStateMapOf<String, Offset>()
    val deviceCoordinates: Map<String, Offset> = _deviceCoordinates
    val anchorDistances = mutableStateMapOf<String, Float>()

    fun setCoordinate(macAddress: String, x: Float, y: Float) {
        _deviceCoordinates[macAddress] = Offset(x, y)
    }

    var currentRtlsLocation by mutableStateOf<Offset?>(null)
        private set

    fun setCurrentLocation(offset: Offset) {
        currentRtlsLocation = offset
    }

    fun updateAnchorDistances(map: Map<String, Float>) {
        anchorDistances.clear()
        anchorDistances.putAll(map)
    }
}