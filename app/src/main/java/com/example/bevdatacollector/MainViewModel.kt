package com.example.bevdatacollector

import androidx.lifecycle.ViewModel
import com.example.bevdatacollector.utils.CalibrationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

class MainViewModel : ViewModel() {

    private val _isCalibrated = MutableStateFlow(false)
    val isCalibrated: StateFlow<Boolean> = _isCalibrated.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()

    fun updateCalibrationStatus(calibrationHelper: CalibrationHelper) {
        _isCalibrated.value = calibrationHelper.isCalibrated()
        Log.d("MainViewModel", "Calibration status updated: ${_isCalibrated.value}")
    }

    fun updatePermissionsStatus(hasCamera: Boolean, hasLocation: Boolean) {
        _permissionsGranted.value = hasCamera && hasLocation
    }
}