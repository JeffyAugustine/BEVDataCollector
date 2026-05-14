package com.example.bevdatacollector.tracking

import kotlin.math.max
import kotlin.math.min

object IouMatching {
    private const val INFINITY_COST = 1e5f

    fun iou(bbox: FloatArray, candidates: Array<FloatArray>): FloatArray {
        val bboxX1 = bbox[0]
        val bboxY1 = bbox[1]
        val bboxX2 = bbox[0] + bbox[2]
        val bboxY2 = bbox[1] + bbox[3]
        val bboxArea = bbox[2] * bbox[3]

        return candidates.map { candidate ->
            val candX1 = candidate[0]
            val candY1 = candidate[1]
            val candX2 = candidate[0] + candidate[2]
            val candY2 = candidate[1] + candidate[3]

            val interX1 = max(bboxX1, candX1)
            val interY1 = max(bboxY1, candY1)
            val interX2 = min(bboxX2, candX2)
            val interY2 = min(bboxY2, candY2)

            val interWidth = max(0f, interX2 - interX1)
            val interHeight = max(0f, interY2 - interY1)
            val interArea = interWidth * interHeight

            val candArea = candidate[2] * candidate[3]
            val unionArea = bboxArea + candArea - interArea

            if (unionArea > 0) interArea / unionArea else 0f
        }.toFloatArray()
    }

    fun iouCost(tracks: List<Track>, detections: List<Detection>,
                trackIndices: IntArray, detectionIndices: IntArray): Array<FloatArray> {
        val costMatrix = Array(trackIndices.size) { FloatArray(detectionIndices.size) { INFINITY_COST } }

        for (row in trackIndices.indices) {
            val trackIdx = trackIndices[row]
            if (tracks[trackIdx].timeSinceUpdate > 1) continue

            val bbox = tracks[trackIdx].toTlwh()
            val candidates = detectionIndices.map { detections[it].tlwh }.toTypedArray()
            val ious = iou(bbox, candidates)

            for (col in detectionIndices.indices) {
                costMatrix[row][col] = 1f - ious[col]
            }
        }
        return costMatrix
    }
}