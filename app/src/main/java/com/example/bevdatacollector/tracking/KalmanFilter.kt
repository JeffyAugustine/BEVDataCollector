package com.example.bevdatacollector.tracking

import kotlin.math.pow

class KalmanFilter {
    private val ndim = 4
    private val dt = 1.0f

    // Motion matrix (8x8)
    private val motionMat = Array(2 * ndim) { i ->
        FloatArray(2 * ndim) { j ->
            when {
                i == j -> 1.0f
                i < ndim && j >= ndim && i + ndim == j -> dt
                else -> 0.0f
            }
        }
    }

    // Update matrix (4x8)
    private val updateMat = Array(ndim) { i ->
        FloatArray(2 * ndim) { j ->
            if (i == j) 1.0f else 0.0f
        }
    }

    private val stdWeightPosition = 1.0f / 20.0f
    private val stdWeightVelocity = 1.0f / 160.0f

    fun initiate(measurement: FloatArray): Pair<FloatArray, Array<FloatArray>> {
        val mean = FloatArray(8)
        measurement.copyInto(mean, 0, 0, 4)

        val h = measurement[3]
        val std = floatArrayOf(
            2f * stdWeightPosition * h,
            2f * stdWeightPosition * h,
            1e-2f,
            2f * stdWeightPosition * h,
            10f * stdWeightVelocity * h,
            10f * stdWeightVelocity * h,
            1e-5f,
            10f * stdWeightVelocity * h
        )

        val covariance = Array(8) { i ->
            FloatArray(8) { j ->
                if (i == j) std[i] * std[i] else 0.0f
            }
        }
        return Pair(mean, covariance)
    }

    fun predict(mean: FloatArray, covariance: Array<FloatArray>): Pair<FloatArray, Array<FloatArray>> {
        val h = mean[3]
        val stdPos = floatArrayOf(
            stdWeightPosition * h,
            stdWeightPosition * h,
            1e-2f,
            stdWeightPosition * h
        )
        val stdVel = floatArrayOf(
            stdWeightVelocity * h,
            stdWeightVelocity * h,
            1e-5f,
            stdWeightVelocity * h
        )

        val motionCov = Array(8) { i ->
            FloatArray(8) { j ->
                if (i == j) {
                    val v = if (i < 4) stdPos[i] else stdVel[i - 4]
                    v * v
                } else 0.0f
            }
        }

        val newMean = multiplyMatrixVector(motionMat, mean)
        val newCov = addMatrices(
            multiplyMatrices(
                multiplyMatrices(motionMat, covariance),
                transpose(motionMat)
            ),
            motionCov
        )

        return Pair(newMean, newCov)
    }

    fun update(mean: FloatArray, covariance: Array<FloatArray>, measurement: FloatArray): Pair<FloatArray, Array<FloatArray>> {
        val h = mean[3]
        val std = floatArrayOf(
            stdWeightPosition * h,
            stdWeightPosition * h,
            1e-1f,
            stdWeightPosition * h
        )

        val innovationCov = Array(4) { i ->
            FloatArray(4) { j ->
                if (i == j) std[i] * std[i] else 0.0f
            }
        }

        val projectedMean = multiplyMatrixVector(updateMat, mean)
        val projectedCov = addMatrices(
            multiplyMatrices(
                multiplyMatrices(updateMat, covariance),
                transpose(updateMat)
            ),
            innovationCov
        )

        val kalmanGain = multiplyMatrices(covariance, transpose(updateMat))

        val innovation = FloatArray(4)
        for (i in 0 until 4) {
            innovation[i] = measurement[i] - projectedMean[i]
        }

        val newMean = FloatArray(8)
        for (i in 0 until 8) {
            newMean[i] = mean[i]
            for (j in 0 until 4) {
                newMean[i] += kalmanGain[i][j] * innovation[j]
            }
        }

        return Pair(newMean, covariance)
    }

    // Helper matrix operations
    private fun multiplyMatrixVector(mat: Array<FloatArray>, vec: FloatArray): FloatArray {
        return FloatArray(mat.size) { i ->
            var sum = 0.0f
            for (j in vec.indices) {
                sum += mat[i][j] * vec[j]
            }
            sum
        }
    }

    private fun multiplyMatrices(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val result = Array(a.size) { FloatArray(b[0].size) }
        for (i in a.indices) {
            for (j in b[0].indices) {
                var sum = 0.0f
                for (k in a[0].indices) {
                    sum += a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    private fun addMatrices(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        return Array(a.size) { i ->
            FloatArray(a[0].size) { j ->
                a[i][j] + b[i][j]
            }
        }
    }

    private fun transpose(mat: Array<FloatArray>): Array<FloatArray> {
        val result = Array(mat[0].size) { FloatArray(mat.size) }
        for (i in mat.indices) {
            for (j in mat[0].indices) {
                result[j][i] = mat[i][j]
            }
        }
        return result
    }
}