package com.example.bevdatacollector.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "ReIDFeatureExtractor"

class ReIDFeatureExtractor(private val context: Context, modelPath: String = "mobilenet_reid.tflite") {
    private var interpreter: Interpreter? = null
    private val inputSize = 128
    private val embeddingDim = 576

    init {
        loadModel(modelPath)
    }

    private fun loadModel(modelPath: String) {
        try {
            val modelFile = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(mappedByteBuffer)
            fileChannel.close()
            inputStream.close()
            Log.d(TAG, " Re-ID model loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Re-ID model: ${e.message}")
        }
    }

    fun extractFeature(bitmap: Bitmap): FloatArray? {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(embeddingDim) }
        interpreter?.run(inputBuffer, output)

        // Normalize
        val feature = output[0]
        var norm = 0f
        for (v in feature) norm += v * v
        norm = kotlin.math.sqrt(norm)
        if (norm > 0) {
            for (i in feature.indices) feature[i] /= norm
        }

        return feature
    }

    fun extractFeatureFromBbox(bitmap: Bitmap, bbox: FloatArray): FloatArray? {
        val x = bbox[0].toInt().coerceAtLeast(0)
        val y = bbox[1].toInt().coerceAtLeast(0)
        val width = bbox[2].toInt().coerceAtMost(bitmap.width - x)
        val height = bbox[3].toInt().coerceAtMost(bitmap.height - y)

        if (width <= 0 || height <= 0) return null

        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height)
        return extractFeature(croppedBitmap)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}