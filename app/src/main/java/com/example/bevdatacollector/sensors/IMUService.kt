package com.example.bevdatacollector.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bevdatacollector.data.IMUData
import kotlin.math.*

class IMUService(private val context: Context) : SensorEventListener {
    private val TAG = "IMUService"

    private lateinit var sensorManager: SensorManager
    private var isTracking = false

    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private var gyroReading = FloatArray(3)

    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var hasGyro = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastGyroTimestamp = 0L
    private var filteredPitch = 0f
    private var filteredRoll = 0f
    private var filteredHeading = 0f
    private val alpha = 0.96f

    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var currentAltitude = 0.0

    fun startIMUTracking() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Log.e(TAG, "No accelerometer available")
        }

        if (magnetometer != null) {
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Log.e(TAG, "No magnetometer available")
        }

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST)
            hasGyro = true
            Log.d(TAG, " Gyroscope available for fusion")
        } else {
            Log.w(TAG, "No gyroscope available - orientation may be noisy")
        }

        isTracking = true
        Log.d(TAG, " IMU tracking started")
    }

    fun updateLocation(latitude: Double, longitude: Double, altitude: Double) {
        currentLatitude = latitude
        currentLongitude = longitude
        currentAltitude = altitude
    }

    fun stopIMUTracking() {
        if (isTracking) {
            sensorManager.unregisterListener(this)
            isTracking = false
            Log.d(TAG, " IMU tracking stopped")
        }
    }

    fun getCurrentIMUData(): IMUData {
        var rawAzimuth = 0f
        var rawPitch = 0f
        var rawRoll = 0f

        if (hasAccelerometer && hasMagnetometer) {
            val success = SensorManager.getRotationMatrix(
                rotationMatrix, null,
                accelerometerReading, magnetometerReading
            )

            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                rawAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                if (rawAzimuth < 0) rawAzimuth += 360
            }
        }

        if (hasGyro && lastGyroTimestamp > 0) {
            val currentTime = System.nanoTime()
            val dt = (currentTime - lastGyroTimestamp) / 1_000_000_000.0f

            val gyroPitch = filteredPitch + gyroReading[1] * dt
            val gyroRoll = filteredRoll + gyroReading[0] * dt
            val gyroHeading = filteredHeading + gyroReading[2] * dt

            filteredPitch = alpha * gyroPitch + (1 - alpha) * rawPitch
            filteredRoll = alpha * gyroRoll + (1 - alpha) * rawRoll
            filteredHeading = alpha * gyroHeading + (1 - alpha) * rawAzimuth
        } else {
            filteredPitch = rawPitch
            filteredRoll = rawRoll
            filteredHeading = rawAzimuth
        }

        lastGyroTimestamp = System.nanoTime()

        // LANDSCAPE transformation (charger on right)
        // Phone is rotated 90° counter-clockwise from portrait
        val correctedHeading = (filteredHeading + 90) % 360
        val correctedPitch = -filteredRoll   // Camera pitch (looking up/down)
        val correctedRoll = filteredPitch    // Camera roll (leaning left/right)

        // Apply magnetic declination
        val trueHeading = applyMagneticDeclination(correctedHeading)

        return IMUData(
            azimuth = trueHeading,
            pitch = correctedPitch,
            roll = correctedRoll,
            accelX = accelerometerReading[0],
            accelY = accelerometerReading[1],
            accelZ = accelerometerReading[2],
            gyroX = gyroReading[0],
            gyroY = gyroReading[1],
            gyroZ = gyroReading[2],
            timestamp = System.currentTimeMillis()
        )
    }

    private fun applyMagneticDeclination(magneticHeading: Float): Float {
        try {
            val geoField = android.hardware.GeomagneticField(
                currentLatitude.toFloat(),
                currentLongitude.toFloat(),
                currentAltitude.toFloat(),
                System.currentTimeMillis()
            )
            val declination = geoField.declination
            var trueHeading = magneticHeading + declination
            if (trueHeading < 0) trueHeading += 360
            if (trueHeading >= 360) trueHeading -= 360
            return trueHeading
        } catch (e: Exception) {
            Log.w(TAG, "Could not get magnetic declination: ${e.message}")
            return magneticHeading
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                hasAccelerometer = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                hasMagnetometer = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, gyroReading, 0, gyroReading.size)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}