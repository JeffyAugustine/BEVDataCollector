package com.example.bevdatacollector.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(private val context: Context) {
    private val TAG = "CameraController"

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView, lifecycleOwner)
                Log.d(TAG, "Camera initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("CameraProvider is null")

        cameraProvider.unbindAll()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            )
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "Binding failed: ${e.message}")
        }
    }

    fun getActualImageResolution(imageFile: File): Pair<Int, Int> {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imageFile.absolutePath, options)

        val width = options.outWidth
        val height = options.outHeight

        Log.d(TAG, " Actual image resolution: ${width}x${height}")
        return Pair(width, height)
    }

    fun takePictureToFile(
        imageFile: File,
        onSuccess: (file: File, captureTimeNanos: Long) -> Unit,
        onError: (exception: Exception) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError(Exception("ImageCapture not initialized"))
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        val captureStartTime = System.nanoTime()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d(TAG, " Image saved: ${imageFile.name} to folder: ${imageFile.parentFile?.name}")
                    onSuccess(imageFile, captureStartTime)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, " Image capture failed: ${exception.message}")
                    onError(exception)
                }
            }
        )
    }

    fun takePictureWithTimestamp(
        onSuccess: (file: File, captureTimeNanos: Long) -> Unit,
        onError: (exception: Exception) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError(Exception("ImageCapture not initialized"))
            return
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val storageDir = context.getExternalFilesDir(null)
        val imageFile = File(storageDir, "BEV_$name.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        val captureStartTime = System.nanoTime()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val captureTimeNanos = captureStartTime
                    Log.d(TAG, " Image saved: ${imageFile.name} at nanos: $captureTimeNanos")
                    onSuccess(imageFile, captureTimeNanos)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, " Image capture failed: ${exception.message}")
                    onError(exception)
                }
            }
        )
    }


    fun takePicture(
        onSuccess: (file: File) -> Unit,
        onError: (exception: Exception) -> Unit
    ) {
        takePictureWithTimestamp(
            onSuccess = { file, _ -> onSuccess(file) },
            onError = onError
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}