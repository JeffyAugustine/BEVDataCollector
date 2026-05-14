package com.example.bevdatacollector.tracking

import android.graphics.RectF
import kotlin.math.sqrt

class Track(
    val mean: FloatArray,
    val covariance: Array<FloatArray>,
    val trackId: Int,
    private val nInit: Int,
    private val maxAge: Int,
    feature: FloatArray?
) {
    var hits: Int = 1
    var age: Int = 1
    var timeSinceUpdate: Int = 0
    var state: TrackState = TrackState.TENTATIVE
    val features: MutableList<FloatArray> = mutableListOf()
    var trackedObject: Any? = null
    var objType: Int = 0

    init {
        feature?.let { features.add(it) }
    }

    fun toTlwh(): FloatArray {
        val ret = mean.copyOf(4)
        ret[2] *= ret[3]  // width = aspect * height
        ret[0] -= ret[2] / 2  // x = center_x - width/2
        ret[1] -= ret[3] / 2  // y = center_y - height/2
        return ret
    }

    fun toTlbr(): FloatArray {
        val tlwh = toTlwh()
        return floatArrayOf(
            tlwh[0],
            tlwh[1],
            tlwh[0] + tlwh[2],
            tlwh[1] + tlwh[3]
        )
    }

    fun predict(kf: KalmanFilter) {
        val (newMean, newCov) = kf.predict(mean, covariance)
        mean.forEachIndexed { i, _ -> mean[i] = newMean[i] }
        covariance.forEachIndexed { i, row -> row.forEachIndexed { j, _ -> covariance[i][j] = newCov[i][j] } }
        age++
        timeSinceUpdate++
    }

    fun update(kf: KalmanFilter, detection: Detection) {
        val xyah = detection.toXyah()
        val (newMean, newCov) = kf.update(mean, covariance, xyah)
        mean.forEachIndexed { i, _ -> mean[i] = newMean[i] }
        covariance.forEachIndexed { i, row -> row.forEachIndexed { j, _ -> covariance[i][j] = newCov[i][j] } }
        features.add(detection.feature)
        objType = detection.objType
        hits++
        timeSinceUpdate = 0

        if (state == TrackState.TENTATIVE && hits >= nInit) {
            state = TrackState.CONFIRMED
        }
    }

    fun markMissed() {
        if (state == TrackState.TENTATIVE) {
            state = TrackState.DELETED
        } else if (timeSinceUpdate > maxAge) {
            state = TrackState.DELETED
        }
    }

    fun isConfirmed(): Boolean = state == TrackState.CONFIRMED
    fun isDeleted(): Boolean = state == TrackState.DELETED
}