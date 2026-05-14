package com.example.bevdatacollector.capture

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bevdatacollector.MainViewModel
import com.example.bevdatacollector.camera.CameraController
import com.example.bevdatacollector.location.LocationService
import com.example.bevdatacollector.sensors.GyroService
import com.example.bevdatacollector.sensors.IMUService
import com.example.bevdatacollector.storage.DataManager
import com.example.bevdatacollector.utils.CalibrationHelper
import kotlinx.coroutines.*
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Services
    val cameraController = remember { CameraController(context) }
    val locationService = remember { LocationService(context) }
    val imuService = remember { IMUService(context) }
    val gyroService = remember { GyroService(context) }
    val dataManager = remember { DataManager(context) }
    val calibrationHelper = remember { CalibrationHelper(context) }

    calibrationHelper.loadOrCreateCalibration()

    // Recording state
    var isRecording by remember { mutableStateOf(false) }
    var frameCount by remember { mutableStateOf(0) }
    var uiStatus by remember { mutableStateOf("Ready") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Calibration check
    val isCalibrated = calibrationHelper.isCalibrated()

    // Job references
    var captureJob by remember { mutableStateOf<Job?>(null) }
    var imuJob by remember { mutableStateOf<Job?>(null) }

    // Show calibration values when screen loads
    LaunchedEffect(Unit) {
        if (isCalibrated) {
            val cal = calibrationHelper.getCalibration()
            android.widget.Toast.makeText(
                context,
                "Using calibration:\nfx=${cal.fx}, fy=${cal.fy}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Clean up when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            captureJob?.cancel()
            imuJob?.cancel()
            try {
                locationService.stopLocationTracking()
                imuService.stopIMUTracking()
                gyroService.stopGyroTracking()
            } catch (e: Exception) {
                Log.e("CaptureScreen", "Cleanup error: ${e.message}")
            }
        }
    }

    // Show warning if not calibrated
    if (!isCalibrated) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("Calibration Required") },
            text = { Text("Please calibrate your camera first before recording.") },
            confirmButton = {
                Button(onClick = onBack) {
                    Text("Go to Calibration")
                }
            }
        )
        return
    }

    // Save dialog when recording stops
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isExporting) {
                    showSaveDialog = false
                    onBack()
                }
            },
            title = { Text("Saving Session") },
            text = {
                Column {
                    Text("Saving ${frameCount} frames...")
                    if (isExporting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (!isExporting) {
                    Button(onClick = {
                        showSaveDialog = false
                        onBack()
                    }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (!isExporting) {
                    TextButton(onClick = {
                        showSaveDialog = false
                        onBack()
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Main layout
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full screen camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    try {
                        cameraController.initializeCamera(this, lifecycleOwner)
                    } catch (e: Exception) {
                        Log.e("CaptureScreen", "Camera init error: ${e.message}")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status overlay
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = uiStatus,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Frames: $frameCount",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (isRecording) "● RECORDING" else "○ STOPPED",
                    color = if (isRecording) Color.Red else Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        // STOP RECORDING
                        scope.launch {
                            try {
                                uiStatus = "Stopping recording..."
                                isRecording = false

                                captureJob?.cancel()
                                imuJob?.cancel()

                                withContext(Dispatchers.IO) {
                                    delay(500)
                                    locationService.stopLocationTracking()
                                    imuService.stopIMUTracking()
                                    gyroService.stopGyroTracking()
                                }

                                showSaveDialog = true
                                isExporting = true

                                val metadataFile = dataManager.createFullMetadataCSV()
                                val totalSizeMB = dataManager.getTotalDataSize() / (1024.0 * 1024.0)

                                withContext(Dispatchers.Main) {
                                    uiStatus = " Exported: $frameCount frames (%.2f MB)".format(totalSizeMB)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Session saved!\n${metadataFile.absolutePath}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Log.e("CaptureScreen", "Stop error: ${e.message}")
                                android.widget.Toast.makeText(
                                    context,
                                    "Error: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    } else {
                        // START RECORDING
                        scope.launch {
                            try {
                                frameCount = 0
                                isRecording = true
                                uiStatus = "Starting sensors..."

                                dataManager.startNewSession()

                                withContext(Dispatchers.IO) {
                                    locationService.startLocationTracking()
                                    imuService.startIMUTracking()
                                    gyroService.startGyroTracking()
                                }

                                locationService.setLocationUpdateListener { timedGPS ->
                                    dataManager.addGPSReading(timedGPS)
                                    Log.d("CaptureScreen", " GPS added to buffer")
                                }

                                uiStatus = "Recording..."

                                val calibration = calibrationHelper.getCalibration()

                                // Start IMU feeding job
                                imuJob = CoroutineScope(Dispatchers.IO).launch {
                                    while (isRecording) {
                                        try {
                                            val currentIMU = imuService.getCurrentIMUData()
                                            dataManager.addIMUReadingFromIMUData(currentIMU)
                                            delay(16)
                                        } catch (e: Exception) {
                                            Log.e("CaptureScreen", "IMU error: ${e.message}")
                                        }
                                    }
                                }

                                // Start capture loop
                                val targetFps = 30
                                val captureIntervalMs = 1000L / targetFps
                                var isCaptureInProgress = false

                                captureJob = CoroutineScope(Dispatchers.IO).launch {
                                    var frameNumber = 0
                                    while (isRecording) {
                                        try {
                                            if (isCaptureInProgress) {
                                                delay(10)
                                                continue
                                            }

                                            val startTime = System.currentTimeMillis()
                                            frameNumber++
                                            val imageFile = dataManager.getImageSavePath(frameNumber)

                                            Log.d("CaptureScreen", " Capturing frame $frameNumber")
                                            isCaptureInProgress = true

                                            cameraController.takePictureToFile(
                                                imageFile,
                                                onSuccess = { savedFile, captureTimeNanos ->
                                                    try {
                                                        frameCount = frameNumber

                                                        val gpsData = dataManager.getInterpolatedGPS(captureTimeNanos)
                                                        val imuData = dataManager.getInterpolatedIMU(captureTimeNanos)
                                                        val (actualWidth, actualHeight) = cameraController.getActualImageResolution(savedFile)

                                                        dataManager.addCapturedFrame(
                                                            imageFile = savedFile,
                                                            gpsData = gpsData,
                                                            imuData = imuData,
                                                            gyroData = null,
                                                            calibration = calibration,
                                                            sequenceNumber = frameNumber,
                                                            imageWidth = actualWidth,
                                                            imageHeight = actualHeight,
                                                            captureTimeNanos = captureTimeNanos
                                                        )

                                                        CoroutineScope(Dispatchers.Main).launch {
                                                            uiStatus = "Frame $frameNumber"
                                                        }

                                                        Log.d("CaptureScreen", " Frame $frameNumber saved")
                                                    } catch (e: Exception) {
                                                        Log.e("CaptureScreen", "Frame save error: ${e.message}")
                                                    } finally {
                                                        isCaptureInProgress = false
                                                    }
                                                },
                                                onError = { exception ->
                                                    Log.e("CaptureScreen", "Capture failed: ${exception.message}")
                                                    isCaptureInProgress = false
                                                }
                                            )

                                            val elapsed = System.currentTimeMillis() - startTime
                                            val delayTime = max(0, captureIntervalMs - elapsed)
                                            if (delayTime > 0 && isRecording) {
                                                delay(delayTime)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("CaptureScreen", "Capture loop error: ${e.message}")
                                            isCaptureInProgress = false
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("CaptureScreen", "Start error: ${e.message}")
                                isRecording = false
                                android.widget.Toast.makeText(
                                    context,
                                    "Failed to start: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                enabled = hasPermissions && isCalibrated,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text(if (isRecording) "⏹️ STOP" else "▶️ START", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onBack,
                enabled = !isRecording,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("← BACK", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}