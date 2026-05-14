package com.example.bevdatacollector.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "TrackingService"

// Data classes defined here
data class TrackResult(
    val frameIndex: Int,
    val imageFile: File,
    val tracks: List<TrackInfo>
)

data class TrackInfo(
    val trackId: Int,
    val objType: Int,
    val bbox: FloatArray,  // x1, y1, x2, y2
    val bottomCenter: FloatArray,  // x, y
    val confidence: Float
)

class TrackingService(private val context: Context) {

    private lateinit var detector: YoloDetector
    private lateinit var reidExtractor: ReIDFeatureExtractor
    private lateinit var tracker: Tracker
    private lateinit var metric: NearestNeighborDistanceMetric

    fun initialize(
        detectorModelPath: String = "yolov8n_float32.tflite",
        reidModelPath: String = "mobilenet_reid.tflite"
    ) {
        detector = YoloDetector(context, detectorModelPath)
        reidExtractor = ReIDFeatureExtractor(context, reidModelPath)
        metric = NearestNeighborDistanceMetric("cosine", 0.2f, 100)
        tracker = Tracker(metric)
        Log.d(TAG, "Tracking service initialized with Re-ID")
    }

    suspend fun processSession(
        sessionFolder: File,
        onProgress: (Int, Int, List<Track>) -> Unit
    ): List<TrackResult> = withContext(Dispatchers.IO) {
        val imageFiles = sessionFolder.listFiles { file ->
            file.name.endsWith(".jpg") && file.name.startsWith("BEV_")
        }?.sorted() ?: emptyList()

        Log.d(TAG, "Processing ${imageFiles.size} images with Re-ID")

        val results = mutableListOf<TrackResult>()

        if (imageFiles.isEmpty()) {
            Log.e(TAG, "No images found")
            return@withContext results
        }

        // Reset tracker for new session
        tracker = Tracker(metric)

        var frameIndex = 0

        for (imageFile in imageFiles) {
            try {
                Log.d(TAG, "Processing frame $frameIndex/${imageFiles.size}")

                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                if (bitmap == null) {
                    Log.e(TAG, "Failed to load bitmap")
                    frameIndex++
                    continue
                }

                // Step 1: Detect objects
                val rawDetections = detector.detect(bitmap)
                Log.d(TAG, "Frame $frameIndex: ${rawDetections.size} raw detections")

                // Step 2: Extract Re-ID features for each detection
                val detectionsWithFeatures = mutableListOf<Detection>()

                for (detection in rawDetections) {
                    val feature = reidExtractor.extractFeatureFromBbox(bitmap, detection.tlwh)
                    if (feature != null) {
                        detectionsWithFeatures.add(
                            Detection(
                                tlwh = detection.tlwh,
                                confidence = detection.confidence,
                                feature = feature,
                                objType = detection.objType,
                                bbox = detection.bbox
                            )
                        )
                    } else {
                        // Fallback to original detection if feature extraction fails
                        detectionsWithFeatures.add(detection)
                    }
                }

                Log.d(TAG, "Frame $frameIndex: ${detectionsWithFeatures.size} detections with features")

                // Step 3: Predict next state
                tracker.predict()

                // Step 4: Update tracker
                val trackedTracks = tracker.update(detectionsWithFeatures)
                Log.d(TAG, "Frame $frameIndex: ${trackedTracks.size} active tracks")

                // Step 5: Store results
                val frameResult = TrackResult(
                    frameIndex = frameIndex,
                    imageFile = imageFile,
                    tracks = trackedTracks.map { track ->
                        val tlwh = track.toTlwh()
                        TrackInfo(
                            trackId = track.trackId,
                            objType = track.objType,
                            bbox = floatArrayOf(tlwh[0], tlwh[1], tlwh[0] + tlwh[2], tlwh[1] + tlwh[3]),
                            bottomCenter = floatArrayOf(tlwh[0] + tlwh[2] / 2, tlwh[1] + tlwh[3]),
                            confidence = track.hits.toFloat() / (track.age)
                        )
                    }
                )
                results.add(frameResult)

                // Update progress
                onProgress(frameIndex + 1, imageFiles.size, trackedTracks)

                bitmap.recycle()
                frameIndex++

                delay(10)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame $frameIndex: ${e.message}", e)
                frameIndex++
            }
        }

        Log.d(TAG, "Processing complete. Processed ${results.size} frames")

        return@withContext results
    }

    fun close() {
        detector.close()
        reidExtractor.close()
        Log.d(TAG, "Tracking service closed")
    }
}