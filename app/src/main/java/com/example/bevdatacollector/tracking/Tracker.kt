package com.example.bevdatacollector.tracking

import android.util.Log

private const val TAG = "Tracker"

class Tracker(
    private val metric: NearestNeighborDistanceMetric,
    private val maxIouDistance: Float = 0.7f,
    private val maxAge: Int = 30,
    private val nInit: Int = 3
) {
    private val kf = KalmanFilter()
    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    fun predict() {
        tracks.forEach { it.predict(kf) }
        Log.d(TAG, "Predicted ${tracks.size} tracks")
    }

    fun update(detections: List<Detection>): List<Track> {
        Log.d(TAG, "Update: ${detections.size} detections, ${tracks.size} tracks")

        // Case 1: No detections
        if (detections.isEmpty()) {
            tracks.forEach { it.markMissed() }
            tracks.removeAll { it.isDeleted() }
            return tracks.toList()
        }

        // Case 2: No tracks
        if (tracks.isEmpty()) {
            detections.forEach { initiateTrack(it) }
            return tracks.toList()
        }

        // Create assignment matrix (tracks x detections)
        val numTracks = tracks.size
        val numDetections = detections.size
        val costMatrix = Array(numTracks) { i ->
            FloatArray(numDetections) { j ->
                val iou = computeIou(tracks[i].toTlwh(), detections[j].tlwh)
                1.0f - iou  // Lower IOU = higher cost
            }
        }

        // Track which tracks and detections are matched
        val trackMatched = BooleanArray(numTracks)
        val detectionMatched = BooleanArray(numDetections)
        val matches = mutableListOf<Pair<Track, Detection>>()


        val allPairs = mutableListOf<Triple<Float, Int, Int>>()
        for (i in 0 until numTracks) {
            for (j in 0 until numDetections) {
                allPairs.add(Triple(1.0f - costMatrix[i][j], i, j))  // Store IOU, trackIdx, detectionIdx
            }
        }

        // Sort by IOU descending (highest first)
        allPairs.sortByDescending { it.first }

        for ((iou, trackIdx, detectionIdx) in allPairs) {
            if (!trackMatched[trackIdx] && !detectionMatched[detectionIdx] && iou > maxIouDistance) {
                matches.add(Pair(tracks[trackIdx], detections[detectionIdx]))
                trackMatched[trackIdx] = true
                detectionMatched[detectionIdx] = true
                Log.d(TAG, "Matched track ${tracks[trackIdx].trackId} with detection $detectionIdx (IOU=$iou)")
            }
        }

        // Update matched tracks
        for ((track, detection) in matches) {
            track.update(kf, detection)
        }

        // Mark unmatched tracks as missed
        for (i in 0 until numTracks) {
            if (!trackMatched[i]) {
                tracks[i].markMissed()
                Log.d(TAG, "Track ${tracks[i].trackId} missed")
            }
        }

        // Create new tracks for unmatched detections
        for (j in 0 until numDetections) {
            if (!detectionMatched[j]) {
                initiateTrack(detections[j])
            }
        }

        // Remove dead tracks
        tracks.removeAll { it.isDeleted() }

        Log.d(TAG, "Final: ${tracks.size} tracks")
        return tracks.toList()
    }

    private fun computeIou(box1: FloatArray, box2: FloatArray): Float {
        // box format: [x, y, width, height]
        val x1 = maxOf(box1[0], box2[0])
        val y1 = maxOf(box1[1], box2[1])
        val x2 = minOf(box1[0] + box1[2], box2[0] + box2[2])
        val y2 = minOf(box1[1] + box1[3], box2[1] + box2[3])

        val interArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = box1[2] * box1[3]
        val area2 = box2[2] * box2[3]
        val unionArea = area1 + area2 - interArea

        return if (unionArea > 0) interArea / unionArea else 0f
    }

    private fun initiateTrack(detection: Detection) {
        val (mean, covariance) = kf.initiate(detection.toXyah())
        val newTrack = Track(mean, covariance, nextId, nInit, maxAge, detection.feature)
        newTrack.objType = detection.objType
        tracks.add(newTrack)
        Log.d(TAG, "New track: ID=$nextId (class=${detection.objType})")
        nextId++
    }
}