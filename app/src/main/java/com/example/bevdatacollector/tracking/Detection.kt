package com.example.bevdatacollector.tracking

import android.graphics.RectF

data class Detection(
    val tlwh: FloatArray,  // x, y, width, height
    val confidence: Float,
    val feature: FloatArray,  // Feature vector from YOLO
    val objType: Int = 0,  // 0=person, 1=car, etc.
    val bbox: RectF? = null
) {
    fun toTlbr(): FloatArray {
        return floatArrayOf(
            tlwh[0],
            tlwh[1],
            tlwh[0] + tlwh[2],
            tlwh[1] + tlwh[3]
        )
    }

    fun toXyah(): FloatArray {
        return floatArrayOf(
            tlwh[0] + tlwh[2] / 2,  // center x
            tlwh[1] + tlwh[3] / 2,  // center y
            tlwh[2] / tlwh[3],       // aspect ratio
            tlwh[3]                  // height
        )
    }
}