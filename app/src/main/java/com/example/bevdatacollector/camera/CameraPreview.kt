package com.example.bevdatacollector.camera

import android.content.res.Configuration
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraController: CameraController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    // Check if we're in landscape
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                // Set scale type to fill the view while maintaining aspect ratio
                scaleType = PreviewView.ScaleType.FILL_CENTER

                // Force landscape orientation in preview
                if (!isLandscape) {
                    // Request to stay in landscape (UI will handle)
                }

                cameraController.initializeCamera(this, lifecycleOwner)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}