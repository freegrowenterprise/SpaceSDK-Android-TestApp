package com.growspace.testapp.pages.rtls

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel

class DeviceCoordinateViewModel : ViewModel() {
    private val _deviceCoordinates = mutableStateMapOf<String, Offset>()
    val deviceCoordinates: Map<String, Offset> = _deviceCoordinates
    val anchorDistances = mutableStateMapOf<String, Float>()

    private var sharedPreferences: SharedPreferences? = null

    companion object {
        private const val PREF_NAME = "uwb_device_coordinates"
        private const val KEY_DEVICE_LIST = "device_list"
        private const val KEY_GRID_WIDTH = "grid_width"
        private const val KEY_GRID_HEIGHT = "grid_height"
    }

    // 그리드 크기 (가로 x 세로)
    var gridWidth by mutableStateOf(5)
        private set
    var gridHeight by mutableStateOf(5)
        private set

    fun setGridSize(width: Int, height: Int) {
        gridWidth = width
        gridHeight = height
        saveGridSize()
    }

    private fun saveGridSize() {
        val prefs = sharedPreferences ?: return
        prefs.edit()
            .putInt(KEY_GRID_WIDTH, gridWidth)
            .putInt(KEY_GRID_HEIGHT, gridHeight)
            .apply()
    }

    private fun loadGridSize() {
        val prefs = sharedPreferences ?: return
        gridWidth = prefs.getInt(KEY_GRID_WIDTH, 5)
        gridHeight = prefs.getInt(KEY_GRID_HEIGHT, 5)
    }

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadCoordinates()
        loadGridSize()
    }

    private fun loadCoordinates() {
        val prefs = sharedPreferences ?: return
        val deviceList = prefs.getString(KEY_DEVICE_LIST, "") ?: ""

        if (deviceList.isNotEmpty()) {
            deviceList.split(",").forEach { deviceName ->
                val x = prefs.getFloat("${deviceName}_x", 0f)
                val y = prefs.getFloat("${deviceName}_y", 0f)
                _deviceCoordinates[deviceName] = Offset(x, y)
            }
        }
    }

    fun setCoordinate(macAddress: String, x: Float, y: Float) {
        _deviceCoordinates[macAddress] = Offset(x, y)
    }

    fun saveAllCoordinates() {
        val prefs = sharedPreferences ?: return
        val editor = prefs.edit()

        val deviceList = _deviceCoordinates.keys.joinToString(",")
        editor.putString(KEY_DEVICE_LIST, deviceList)

        _deviceCoordinates.forEach { (name, offset) ->
            editor.putFloat("${name}_x", offset.x)
            editor.putFloat("${name}_y", offset.y)
        }

        editor.apply()
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