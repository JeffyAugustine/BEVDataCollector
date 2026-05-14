package com.example.bevdatacollector.projection

import android.util.Log
import kotlin.math.*

private const val TAG = "GroundPlaneProjector"

class GroundPlaneProjector(
    private val fx: Double,
    private val fy: Double,
    private val cx: Double,
    private val cy: Double,
    private val k1: Double,
    private val k2: Double,
    private val p1: Double,
    private val p2: Double,
    private val k3: Double,
    private val cameraHeight: Double,
    private val headingDeg: Double,
    private val pitchDeg: Double,
    private val rollDeg: Double,
    private val cameraLat: Double,
    private val cameraLon: Double
) {

    private val metersPerDegLat = 111320.0
    private val metersPerDegLon = 111320.0 * cos(Math.toRadians(cameraLat))

    fun projectPixelToLatLon(pixelX: Double, pixelY: Double): Pair<Double, Double>? {
        try {
            //  Undistort point
            val (undistX, undistY) = undistortPoint(pixelX, pixelY)

            //  Convert to normalized coordinates
            val xNorm = (undistX - cx) / fx
            val yNorm = (undistY - cy) / fy

            //  Ray in camera coordinates
            var rayCamera = doubleArrayOf(xNorm, yNorm, 1.0)
            val norm = sqrt(rayCamera[0] * rayCamera[0] + rayCamera[1] * rayCamera[1] + rayCamera[2] * rayCamera[2])
            rayCamera = doubleArrayOf(rayCamera[0] / norm, rayCamera[1] / norm, rayCamera[2] / norm)

            //  Rotate to world coordinates
            val R = rotationMatrix(headingDeg, pitchDeg, rollDeg)
            val rayWorld = doubleArrayOf(
                R[0][0] * rayCamera[0] + R[0][1] * rayCamera[1] + R[0][2] * rayCamera[2],
                R[1][0] * rayCamera[0] + R[1][1] * rayCamera[1] + R[1][2] * rayCamera[2],
                R[2][0] * rayCamera[0] + R[2][1] * rayCamera[1] + R[2][2] * rayCamera[2]
            )

            //  Ground intersection (Z = 0)
            if (rayWorld[2] >= 0) return null

            val t = -cameraHeight / rayWorld[2]
            val east = t * rayWorld[0]
            val north = t * rayWorld[1]

            // : Convert to lat/long
            val deltaLat = north / metersPerDegLat
            val deltaLon = east / metersPerDegLon

            val resultLat = cameraLat + deltaLat
            val resultLon = cameraLon + deltaLon

            return Pair(resultLat, resultLon)

        } catch (e: Exception) {
            Log.e(TAG, "Projection failed: ${e.message}")
            return null
        }
    }

    private fun undistortPoint(x: Double, y: Double): Pair<Double, Double> {
        // Simplified undistortion using OpenCV's formulas
        val x0 = (x - cx) / fx
        val y0 = (y - cy) / fy

        val r2 = x0 * x0 + y0 * y0
        val r4 = r2 * r2
        val r6 = r4 * r2

        val radial = 1 + k1 * r2 + k2 * r4 + k3 * r6
        val xDist = x0 * radial + 2 * p1 * x0 * y0 + p2 * (r2 + 2 * x0 * x0)
        val yDist = y0 * radial + p1 * (r2 + 2 * y0 * y0) + 2 * p2 * x0 * y0

        val undistX = cx + fx * xDist
        val undistY = cy + fy * yDist

        return Pair(undistX, undistY)
    }

    private fun rotationMatrix(headingDeg: Double, pitchDeg: Double, rollDeg: Double): Array<DoubleArray> {
        val h = Math.toRadians(headingDeg)
        val p = Math.toRadians(pitchDeg)
        val r = Math.toRadians(rollDeg)

        val Rz = arrayOf(
            doubleArrayOf(cos(h), -sin(h), 0.0),
            doubleArrayOf(sin(h), cos(h), 0.0),
            doubleArrayOf(0.0, 0.0, 1.0)
        )

        val Rx = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, cos(p), -sin(p)),
            doubleArrayOf(0.0, sin(p), cos(p))
        )

        val Ry = arrayOf(
            doubleArrayOf(cos(r), 0.0, sin(r)),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(-sin(r), 0.0, cos(r))
        )

        // R = Rz @ Rx @ Ry
        val RxRy = multiplyMatrices(Rx, Ry)
        return multiplyMatrices(Rz, RxRy)
    }

    private fun multiplyMatrices(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> {
        val result = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                result[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j]
            }
        }
        return result
    }
}