package com.example.bevdatacollector.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import com.example.bevdatacollector.utils.CalibrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    onCalibrationComplete: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var showInstructions by remember { mutableStateOf(true) }
    var imageCount by remember { mutableStateOf(0) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Calibration variables
    var calibrationImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationProgress by remember { mutableStateOf(0) }
    var calibrationStatus by remember { mutableStateOf("") }

    // Camera state
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Calibration helper and processor
    val calibrationHelper = remember { CalibrationHelper(context) }
    val processor = remember { OpenCVCalibrationProcessor(context) }
    var isCalibrated by remember { mutableStateOf(calibrationHelper.isCalibrated()) }

    // Permission launcher
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusMessage = "Camera permission required for calibration"
        }
    }

    // Instruction Dialog
    if (showInstructions) {
        AlertDialog(
            onDismissRequest = {
                showInstructions = false
            },
            title = {
                Text(
                    text = "📷 Camera Calibration Instructions",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text("• Print a checkerboard pattern (8x6 or 7x5 squares)")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Hold the board in front of the camera")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Take 20-30 photos from different angles and distances")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Keep the entire board visible in each photo")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Move the board around (left, right, up, down, tilted)")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = " The camera will be in LANDSCAPE mode",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstructions = false
                        statusMessage = "Ready for calibration"
                    }
                ) {
                    Text("Got it! Start Calibration")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showInstructions = false
                        if (isCalibrated) {
                            onCalibrationComplete()
                        }
                    }
                ) {
                    Text("Skip")
                }
            }
        )
        return
    }

    // Full screen layout
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full screen camera preview
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_START

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = androidx.camera.core.Preview.Builder().build()
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                                preview.setSurfaceProvider(surfaceProvider)
                                statusMessage = "Ready"
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission request overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera permission required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Top status bar (semi-transparent)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isCalibrated) " Calibrated" else "️ Not Calibrated",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = statusMessage,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (imageCount > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "$imageCount images",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        // Bottom button bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Capture button
            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        statusMessage = "Capturing..."

                        val imageCaptureObj = imageCapture
                        if (imageCaptureObj != null && hasCameraPermission) {
                            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                                .format(System.currentTimeMillis())
                            val storageDir = context.getExternalFilesDir(null)
                            val imageFile = File(storageDir, "calib_$name.jpg")

                            val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

                            imageCaptureObj.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        calibrationImages = calibrationImages + imageFile
                                        imageCount = calibrationImages.size
                                        statusMessage = "Image $imageCount/30 captured"
                                        isProcessing = false

                                        if (imageCount >= 30) {
                                            isCalibrating = true
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        statusMessage = "Capture failed: ${exception.message}"
                                        isProcessing = false
                                    }
                                }
                            )
                        } else {
                            statusMessage = "Camera not ready"
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing && hasCameraPermission && !isCalibrating,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(" Take Photo")
            }

            // Reset button
            if (imageCount > 0) {
                Button(
                    onClick = {
                        calibrationImages = emptyList()
                        imageCount = 0
                        statusMessage = "Reset"
                    },
                    enabled = !isProcessing && !isCalibrating,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Reset")
                }
            }

            // Next/Skip button
            Button(
                onClick = onCalibrationComplete,
                enabled = !isProcessing && !isCalibrating,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCalibrated) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(if (isCalibrated) "Next →" else "Skip →")
            }
        }

        // Calibration progress dialog
        if (isCalibrating) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Calibrating Camera...") },
                text = {
                    Column(
                        modifier = Modifier.width(300.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = calibrationProgress / 100f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(calibrationStatus, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )

            // Run calibration
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val result = processor.calibrateFromImages(
                        calibrationImages,
                        { status, progress ->
                            calibrationStatus = status
                            calibrationProgress = progress
                        }
                    )
                    isCalibrated = calibrationHelper.isCalibrated()

                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            calibrationHelper.setCalibratedValues(
                                fx = result.fx,
                                fy = result.fy,
                                cx = result.cx,
                                cy = result.cy,
                                k1 = result.k1,
                                k2 = result.k2,
                                p1 = result.p1,
                                p2 = result.p2,
                                k3 = result.k3,
                                cameraHeightMeters = 1.7f,
                                gpsOffsetX = 0f,
                                gpsOffsetY = 0f,
                                gpsOffsetZ = 0f
                            )
                            viewModel.updateCalibrationStatus(calibrationHelper)
                            isCalibrated = true  // Update the UI state
                            statusMessage = "Calibration successful!"
                            isCalibrating = false

                            android.widget.Toast.makeText(
                                context,
                                "Calibration successful!\nfx=${result.fx}, fy=${result.fy}\ncx=${result.cx}, cy=${result.cy}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            kotlinx.coroutines.delay(500)
                            onCalibrationComplete()
                        } else {
                            calibrationStatus = "Calibration failed. Need 10+ valid checkerboard images."
                            statusMessage = "Calibration failed. Try again."
                            isCalibrating = false
                            // Clear images so user can retry
                            calibrationImages = emptyList()
                            imageCount = 0
                        }
                    }
                }
            }
        }

        // Loading overlay
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing...", color = Color.White)
                }
            }
        }
    }
}