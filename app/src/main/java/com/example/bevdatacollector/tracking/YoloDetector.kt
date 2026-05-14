package com.example.bevdatacollector.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

private const val TAG = "YoloDetector"

class YoloDetector(private val context: Context, modelPath: String) {
    private var interpreter: Interpreter? = null
    private val inputSize = 320
    private val modelInputChannels = 3

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
            Log.d(TAG, " Model loaded successfully")

            // Log input/output details
            interpreter?.getInputTensor(0)?.shape()?.let { shape ->
                Log.d(TAG, "Input shape: ${shape.joinToString()}")
            }
            interpreter?.getOutputTensor(0)?.shape()?.let { shape ->
                Log.d(TAG, "Output shape: ${shape.joinToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        Log.d(TAG, " detect() called ")

        val startTime = System.currentTimeMillis()

        // Resize to 320x320
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Create float buffer
        val imgData = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        imgData.rewind()

        // Get pixels
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        // Convert to float [-1, 1]
        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f
            imgData.putFloat(r)
            imgData.putFloat(g)
            imgData.putFloat(b)
        }
        imgData.rewind()

        // Get output tensor shape dynamically
        val outputTensor = interpreter?.getOutputTensor(0)
        val outputShape = outputTensor?.shape()

        if (outputShape == null || outputShape.size < 2) {
            Log.e(TAG, "Invalid output shape")
            return emptyList()
        }

        val numDetections = outputShape[1]  // e.g., 300
        val detectionSize = outputShape[2]  // Should be 6

        Log.d(TAG, "Output shape: batch=${outputShape[0]}, numDetections=$numDetections, detectionSize=$detectionSize")

        // Create output array with correct size
        val output = Array(1) { Array(numDetections) { FloatArray(detectionSize) } }

        try {
            interpreter?.run(imgData, output)

            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inference completed in ${inferenceTime}ms")

            val detections = parseDetections(output[0], bitmap.width.toFloat(), bitmap.height.toFloat())
            Log.d(TAG, "Found ${detections.size} detections")

            return detections

        } catch (e: Exception) {
            Log.e(TAG, "Error during inference: ${e.message}", e)
            return emptyList()
        }
    }

    private fun parseDetections(output: Array<FloatArray>, imgWidth: Float, imgHeight: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val confidenceThreshold = 0.3f

        for (i in output.indices) {
            if (output[i].size < 6) continue

            val confidence = output[i][4]

            if (confidence > confidenceThreshold) {
                val x1Norm = output[i][0]
                val y1Norm = output[i][1]
                val x2Norm = output[i][2]
                val y2Norm = output[i][3]
                val classId = output[i][5].toInt()

                // Convert to pixel coordinates
                val x = x1Norm * imgWidth
                val y = y1Norm * imgHeight
                val width = (x2Norm - x1Norm) * imgWidth
                val height = (y2Norm - y1Norm) * imgHeight

                if (width > 0 && height > 0) {
                    Log.d(TAG, "Detection: class=$classId, conf=$confidence")

                    val tlwh = floatArrayOf(x, y, width, height)
                    val bbox = RectF(x, y, x + width, y + height)

                    detections.add(Detection(
                        tlwh = tlwh,
                        confidence = confidence,
                        feature = FloatArray(128) { 0f },
                        objType = classId,
                        bbox = bbox
                    ))
                }
            }
        }

        return detections
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}