package com.example.bevdatacollector.tracking

import kotlin.math.sqrt

class NearestNeighborDistanceMetric(
    private val metric: String,
    val matchingThreshold: Float,
    private val budget: Int? = null
) {
    private val samples = mutableMapOf<Int, MutableList<FloatArray>>()

    fun partialFit(features: Array<FloatArray>, targets: IntArray, activeTargets: List<Int>) {
        for ((idx, target) in targets.withIndex()) {
            samples.getOrPut(target) { mutableListOf() }.add(features[idx])
            budget?.let {
                if (samples[target]!!.size > it) {
                    samples[target] = samples[target]!!.takeLast(it).toMutableList()
                }
            }
        }
        samples.keys.retainAll(activeTargets)
    }

    fun distance(features: Array<FloatArray>, targets: IntArray): Array<FloatArray> {
        val costMatrix = Array(targets.size) { FloatArray(features.size) { Float.MAX_VALUE } }

        for (i in targets.indices) {
            val targetSamples = samples[targets[i]]
            if (targetSamples == null || targetSamples.isEmpty()) {
                continue  // Skip if no samples
            }

            for (j in features.indices) {
                costMatrix[i][j] = when (metric) {
                    "cosine" -> cosineDistance(targetSamples, features[j])
                    else -> euclideanDistance(targetSamples, features[j])
                }
            }
        }
        return costMatrix
    }

    private fun euclideanDistance(samples: List<FloatArray>, feature: FloatArray): Float {
        var minDist = Float.MAX_VALUE
        for (sample in samples) {
            var sum = 0.0f
            for (k in sample.indices) {
                val diff = sample[k] - feature[k]
                sum += diff * diff
            }
            val dist = sqrt(sum)
            if (dist < minDist) {
                minDist = dist
            }
        }
        return minDist
    }

    private fun cosineDistance(samples: List<FloatArray>, feature: FloatArray): Float {
        val normFeature = normalize(feature)
        var minDist = Float.MAX_VALUE
        for (sample in samples) {
            val normSample = normalize(sample)
            var dot = 0.0f
            for (k in normSample.indices) {
                dot += normSample[k] * normFeature[k]
            }
            val dist = 1f - dot
            if (dist < minDist) {
                minDist = dist
            }
        }
        return minDist
    }

    private fun normalize(vec: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (value in vec) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares)
        return if (norm > 0) {
            FloatArray(vec.size) { i -> vec[i] / norm }
        } else {
            vec.copyOf()
        }
    }
}