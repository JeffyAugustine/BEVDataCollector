package com.example.bevdatacollector.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bevdatacollector.data.GyroData

class GyroService(private val context: Context) : SensorEventListener {
    private val TAG = "GyroService"

    private lateinit var sensorManager: SensorManager
    private var isTracking = false

    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    fun startGyroTracking() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
            isTracking = true
            Log.d(TAG, " Gyroscope tracking started")
        } else {
            Log.e(TAG, "No gyroscope available on this device")
        }
    }

    fun stopGyroTracking() {
        if (isTracking) {
            sensorManager.unregisterListener(this)
            isTracking = false
            Log.d(TAG, " Gyroscope tracking stopped")
        }
    }

    fun getCurrentGyroData(): GyroData {
        return GyroData(
            gyroX = gyroX,
            gyroY = gyroY,
            gyroZ = gyroZ,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}