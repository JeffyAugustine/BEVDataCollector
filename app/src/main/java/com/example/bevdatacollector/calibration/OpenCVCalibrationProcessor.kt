package com.example.bevdatacollector.calibration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.bevdatacollector.data.CameraCalibration
import org.opencv.core.*
import org.opencv.android.Utils
import org.opencv.imgproc.Imgproc
import org.opencv.calib3d.Calib3d
import java.io.File

class OpenCVCalibrationProcessor(private val context: Context) {
    private val TAG = "CalibrationProcessor"

    // Checkerboard dimensions - 9x6 INNER CORNERS
    private val boardWidth = 9
    private val boardHeight = 6
    private val boardSize = Size(boardWidth.toDouble(), boardHeight.toDouble())
    private val squareSize = 0.0254  // 2.15cm

    init {
        // Load OpenCV native library
        try {
            System.loadLibrary("opencv_java4")
            Log.d(TAG, " OpenCV library loaded successfully")

            // Test if it's working by creating a simple Mat
            val testMat = Mat()
            testMat.release()
            Log.d(TAG, "✅ OpenCV test passed")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, " Failed to load opencv_java4: ${e.message}")
            Log.e(TAG, "Make sure libopencv_java4.so is in jniLibs/arm64-v8a/")
        } catch (e: Exception) {
            Log.e(TAG, " OpenCV init exception: ${e.message}")
        }
    }

    fun isReady(): Boolean = true

    /**
     * Convert Bitmap to Mat manually without using Utils
     */
    private fun bitmapToMat(bitmap: Bitmap): Mat {
        val width = bitmap.width
        val height = bitmap.height
        val mat = Mat(height, width, CvType.CV_8UC3)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val byteArray = ByteArray(width * height * 3)
        var i = 0
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toByte()
            val g = (pixel shr 8 and 0xFF).toByte()
            val b = (pixel and 0xFF).toByte()
            byteArray[i] = b      // B
            byteArray[i + 1] = g  // G
            byteArray[i + 2] = r  // R
            i += 3
        }

        mat.put(0, 0, byteArray)
        return mat
    }

    fun findCheckerboardCorners(imageFile: File): MatOfPoint2f? {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap")
                return null
            }

            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

            val corners = MatOfPoint2f()
            val found = Calib3d.findChessboardCorners(gray, boardSize, corners)

            if (found) {
                val cornerCount = corners.toList().size
                Log.d(TAG, "Found $cornerCount corners")

                if (cornerCount == boardWidth * boardHeight) {
                    val termCriteria = TermCriteria(
                        TermCriteria.EPS + TermCriteria.MAX_ITER,
                        30, 0.1
                    )
                    Imgproc.cornerSubPix(gray, corners, Size(5.0, 5.0), Size(-1.0, -1.0), termCriteria)
                    mat.release()
                    gray.release()
                    return corners
                }
            }

            mat.release()
            gray.release()

        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }

        return null
    }

    suspend fun calibrateFromImages(
        imageFiles: List<File>,
        onProgress: (String, Int) -> Unit
    ): CameraCalibration? {
        // Verify OpenCV is working
        try {
            val testMat = Mat()
            testMat.release()
            Log.d(TAG, "OpenCV verification passed")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "OpenCV not loaded: ${e.message}")
            onProgress("OpenCV library not loaded. Please restart app.", 0)
            return null
        }

        onProgress("Finding checkerboard corners...", 0)

        val objectPoints = mutableListOf<Mat>()
        val imagePoints = mutableListOf<Mat>()

        // Create object points (3D points of the checkerboard)
        val objPointsList = mutableListOf<org.opencv.core.Point3>()
        for (i in 0 until boardHeight) {
            for (j in 0 until boardWidth) {
                objPointsList.add(org.opencv.core.Point3(j.toDouble() * squareSize, i.toDouble() * squareSize, 0.0))
            }
        }

        val objPointsMat = MatOfPoint3f()
        objPointsMat.fromList(objPointsList)

        var validImages = 0
        var imageSize: Size? = null

        for ((index, imageFile) in imageFiles.withIndex()) {
            val progress = (index.toFloat() / imageFiles.size * 100).toInt()
            onProgress("Processing image ${index + 1}/${imageFiles.size} ($validImages valid)", progress)

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                Log.w(TAG, "Failed to load bitmap")
                continue
            }

            // Get image size from first valid image
            if (imageSize == null) {
                imageSize = Size(bitmap.width.toDouble(), bitmap.height.toDouble())
            }

            val corners = findCheckerboardCorners(imageFile)

            if (corners != null && corners.toList().size == boardWidth * boardHeight) {
                objectPoints.add(objPointsMat)
                imagePoints.add(corners)
                validImages++
                Log.d(TAG, "✅ Image ${index + 1}: Valid - ${corners.toList().size} corners")
            } else {
                val cornerCount = corners?.toList()?.size ?: 0
                Log.w(TAG, "❌ Image ${index + 1}: Invalid - found $cornerCount corners, expected ${boardWidth * boardHeight}")
            }
        }

        if (validImages < 10) {
            onProgress("Only $validImages valid images. Need 10+", 100)
            return null
        }

        onProgress("Calculating camera matrix using $validImages images...", 80)

        // Create empty matrices for OpenCV to auto-initialize
        val cameraMatrix = Mat()
        val distCoeffs = Mat()
        val rvecs = mutableListOf<Mat>()
        val tvecs = mutableListOf<Mat>()

        val calibrationFlags = Calib3d.CALIB_FIX_ASPECT_RATIO

        try {
            Calib3d.calibrateCamera(
                objectPoints,
                imagePoints,
                imageSize!!,
                cameraMatrix,
                distCoeffs,
                rvecs,
                tvecs,
                calibrationFlags
            )
        } catch (e: Exception) {
            Log.e(TAG, "Calibration failed: ${e.message}")
            onProgress("Calibration failed: ${e.message}", 100)
            return null
        }

        // Extract values
        val fx = cameraMatrix.get(0, 0)[0].toFloat()
        val fy = cameraMatrix.get(1, 1)[0].toFloat()
        val cx = cameraMatrix.get(0, 2)[0].toFloat()
        val cy = cameraMatrix.get(1, 2)[0].toFloat()

        val distArray = DoubleArray(5)
        distCoeffs.get(0, 0, distArray)
        val k1 = distArray[0].toFloat()
        val k2 = distArray[1].toFloat()
        val p1 = distArray[2].toFloat()
        val p2 = distArray[3].toFloat()
        val k3 = distArray[4].toFloat()

        // Clean up Mats
        cameraMatrix.release()
        distCoeffs.release()
        rvecs.forEach { it.release() }
        tvecs.forEach { it.release() }
        objectPoints.forEach { it.release() }
        imagePoints.forEach { it.release() }

        onProgress("Calibration complete!", 100)

        Log.d(TAG, " Calibration: fx=$fx, fy=$fy, cx=$cx, cy=$cy")
        Log.d(TAG, " Distortion: k1=$k1, k2=$k2, p1=$p1, p2=$p2, k3=$k3")

        return CameraCalibration.calibrated(
            fx = fx,
            fy = fy,
            cx = cx,
            cy = cy,
            k1 = k1,
            k2 = k2,
            p1 = p1,
            p2 = p2,
            k3 = k3,
            cameraHeightMeters = 1.7f,
            gpsOffsetX = 0f,
            gpsOffsetY = 0f,
            gpsOffsetZ = 0f
        )
    }
}