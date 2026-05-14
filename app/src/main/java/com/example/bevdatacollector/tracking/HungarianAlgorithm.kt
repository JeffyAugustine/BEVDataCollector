package com.example.bevdatacollector.tracking

class HungarianAlgorithm {
    companion object {
        fun linearAssignment(costMatrix: Array<FloatArray>): Pair<IntArray, IntArray> {
            // Handle empty matrix
            if (costMatrix.isEmpty()) {
                return Pair(intArrayOf(), intArrayOf())
            }

            val n = costMatrix.size
            val m = costMatrix[0].size

            // If no columns, return empty
            if (m == 0) {
                return Pair(intArrayOf(), intArrayOf())
            }

            val u = FloatArray(n + 1)
            val v = FloatArray(m + 1)
            val p = IntArray(m + 1)
            val way = IntArray(m + 1)

            for (i in 1..n) {
                p[0] = i
                var j0 = 0
                val minv = FloatArray(m + 1) { Float.MAX_VALUE }
                val used = BooleanArray(m + 1)

                do {
                    used[j0] = true
                    val i0 = p[j0]
                    var delta = Float.MAX_VALUE
                    var j1 = 0

                    for (j in 1..m) {
                        if (!used[j]) {
                            // SAFETY: Check bounds
                            val i0Idx = i0 - 1
                            val jIdx = j - 1
                            if (i0Idx in costMatrix.indices && jIdx in costMatrix[0].indices) {
                                val cur = costMatrix[i0Idx][jIdx] - u[i0] - v[j]
                                if (cur < minv[j]) {
                                    minv[j] = cur
                                    way[j] = j0
                                }
                                if (minv[j] < delta) {
                                    delta = minv[j]
                                    j1 = j
                                }
                            }
                        }
                    }

                    for (j in 0..m) {
                        if (used[j]) {
                            u[p[j]] += delta
                            v[j] -= delta
                        } else {
                            minv[j] -= delta
                        }
                    }
                    j0 = j1
                } while (p[j0] != 0)

                do {
                    val j1 = way[j0]
                    p[j0] = p[j1]
                    j0 = j1
                } while (j0 != 0)
            }

            // Build assignment arrays
            val assignment = IntArray(n) { -1 }
            val cols = IntArray(m) { -1 }

            for (j in 1..m) {
                if (p[j] != 0) {
                    val rowIdx = p[j] - 1
                    if (rowIdx in assignment.indices) {
                        assignment[rowIdx] = j - 1
                    }
                    if (rowIdx in cols.indices) {
                        cols[rowIdx] = j - 1
                    }
                }
            }

            return Pair(assignment, cols)
        }
    }
}