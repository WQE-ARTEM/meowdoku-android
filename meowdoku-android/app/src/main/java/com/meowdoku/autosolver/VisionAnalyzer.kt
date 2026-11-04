package com.meowdoku.autosolver

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.sqrt

object VisionAnalyzer {

    data class Rgb(val r: Float, val g: Float, val b: Float) {
        fun distanceTo(other: Rgb): Float {
            val dr = r - other.r
            val dg = g - other.g
            val db = b - other.b
            return sqrt(dr * dr + dg * dg + db * db)
        }
    }

    private class Cluster(
        val cells: MutableList<Pair<Int, Int>>,
        var center: Rgb
    )

    /**
     * Извлекает матрицу регионов N x N из участка скриншота boardRect.
     * Семплирует внутреннюю часть каждой клетки и объединяет цвета ровно в N кластеров.
     */
    fun extractRegionGrid(bitmap: Bitmap, boardRect: RectF, n: Int): Array<IntArray> {
        val cellW = boardRect.width() / n.toFloat()
        val cellH = boardRect.height() / n.toFloat()

        val cellColors = Array(n) { Array(n) { Rgb(0f, 0f, 0f) } }

        // Семплируем кольцо вокруг центра клетки, избегая черных границ сетки и центральной точки
        val sampleOffsets = listOf(
            Pair(-0.22f, -0.22f), Pair(0.22f, -0.22f),
            Pair(-0.22f,  0.22f), Pair(0.22f,  0.22f),
            Pair(0.0f,   -0.25f), Pair(0.0f,   0.25f),
            Pair(-0.25f,  0.0f),  Pair(0.25f,  0.0f)
        )

        for (r in 0 until n) {
            for (c in 0 until n) {
                val cx = boardRect.left + (c + 0.5f) * cellW
                val cy = boardRect.top + (r + 0.5f) * cellH

                val rSamples = mutableListOf<Int>()
                val gSamples = mutableListOf<Int>()
                val bSamples = mutableListOf<Int>()

                for ((ox, oy) in sampleOffsets) {
                    val px = (cx + ox * cellW).toInt().coerceIn(0, bitmap.width - 1)
                    val py = (cy + oy * cellH).toInt().coerceIn(0, bitmap.height - 1)
                    val pixel = bitmap.getPixel(px, py)
                    rSamples.add(Color.red(pixel))
                    gSamples.add(Color.green(pixel))
                    bSamples.add(Color.blue(pixel))
                }

                rSamples.sort()
                gSamples.sort()
                bSamples.sort()
                val mid = rSamples.size / 2
                cellColors[r][c] = Rgb(
                    rSamples[mid].toFloat(),
                    gSamples[mid].toFloat(),
                    bSamples[mid].toFloat()
                )
            }
        }

        // Начальная группировка близких по цвету клеток (порог евклидова расстояния = 22)
        val clusters = mutableListOf<Cluster>()
        val initialThreshold = 22f

        for (r in 0 until n) {
            for (c in 0 until n) {
                val color = cellColors[r][c]
                var bestCluster: Cluster? = null
                var bestDist = Float.MAX_VALUE

                for (cluster in clusters) {
                    val dist = color.distanceTo(cluster.center)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestCluster = cluster
                    }
                }

                if (bestCluster != null && bestDist <= initialThreshold) {
                    val count = bestCluster.cells.size
                    bestCluster.center = Rgb(
                        (bestCluster.center.r * count + color.r) / (count + 1),
                        (bestCluster.center.g * count + color.g) / (count + 1),
                        (bestCluster.center.b * count + color.b) / (count + 1)
                    )
                    bestCluster.cells.add(Pair(r, c))
                } else {
                    clusters.add(Cluster(mutableListOf(Pair(r, c)), color))
                }
            }
        }

        // Иерархическое слияние самых близких кластеров, пока их число превышает N
        while (clusters.size > n) {
            var mergeI = 0
            var mergeJ = 1
            var minDist = Float.MAX_VALUE

            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val dist = clusters[i].center.distanceTo(clusters[j].center)
                    if (dist < minDist) {
                        minDist = dist
                        mergeI = i
                        mergeJ = j
                    }
                }
            }

            val c1 = clusters[mergeI]
            val c2 = clusters[mergeJ]
            val total = c1.cells.size + c2.cells.size
            c1.center = Rgb(
                (c1.center.r * c1.cells.size + c2.center.r * c2.cells.size) / total,
                (c1.center.g * c1.cells.size + c2.center.g * c2.cells.size) / total,
                (c1.center.b * c1.cells.size + c2.center.b * c2.cells.size) / total
            )
            c1.cells.addAll(c2.cells)
            clusters.removeAt(mergeJ)
        }

        val resultGrid = Array(n) { IntArray(n) }
        for ((regionId, cluster) in clusters.withIndex()) {
            for ((r, c) in cluster.cells) {
                resultGrid[r][c] = regionId
            }
        }
        return resultGrid
    }
}
