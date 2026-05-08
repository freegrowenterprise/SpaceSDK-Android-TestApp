package com.growspace.testapp

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf

class BLEViewModel : ViewModel() {
    private val _devices = mutableStateListOf<Pair<String, Int>>()
    val devices: List<Pair<String, Int>> get() = _devices

    fun addDevice(macAddress: String, rssi: Int) {
        if (!_devices.any { it.first == macAddress }) {
            _devices.add(macAddress to rssi)
        }
    }
}