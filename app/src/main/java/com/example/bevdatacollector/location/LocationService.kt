package com.example.bevdatacollector.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bevdatacollector.data.GPSData
import com.example.bevdatacollector.storage.TimedGPS
import com.google.android.gms.location.*

class LocationService(private val context: Context) {
    private val TAG = "LocationService"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var isTracking = false
    private var locationCallback: LocationCallback? = null
    private var onLocationUpdate: ((TimedGPS) -> Unit)? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        100L
    ).setMinUpdateIntervalMillis(50L).build()

    fun setLocationUpdateListener(listener: (TimedGPS) -> Unit) {
        onLocationUpdate = listener
    }

    fun startLocationTracking() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    val timedGPS = TimedGPS(
                        timestamp = location.time,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        accuracy = location.accuracy,
                        speed = location.speed,
                        bearing = location.bearing,
                        provider = location.provider ?: "gps"
                    )
                    onLocationUpdate?.invoke(timedGPS)
                    Log.d(TAG, " Location update: ${location.latitude}, ${location.longitude}")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTracking = true
            Log.d(TAG, " Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, " Location permission denied: ${e.message}")
        }
    }

    fun stopLocationTracking() {
        if (isTracking && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
            isTracking = false
            Log.d(TAG, " Location tracking stopped")
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}