package com.meowdoku.autosolver

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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

    data class AutoSolveAnalysis(
        val boardRect: RectF,
        val gridSize: Int,
        val regionGrid: Array<IntArray>,
        val existingCats: List<Pair<Int, Int>>,
        val solution: List<Pair<Int, Int>>?
    )

    private class Cluster(
        val cells: MutableList<Pair<Int, Int>>,
        var center: Rgb
    )

    fun analyzeAndSolve(
        bitmap: Bitmap,
        fallbackRect: RectF,
        forcedGridSize: Int? = null,
        autoDetectRect: Boolean = true
    ): AutoSolveAnalysis {
        val detectedRect = if (autoDetectRect) {
            detectExactBoardRect(bitmap, fallbackRect)
        } else {
            RectF(fallbackRect)
        }

        val candidateSizes = if (forcedGridSize != null && forcedGridSize in 5..15) {
            listOf(forcedGridSize)
        } else {
            detectGridSizeCandidates(bitmap, detectedRect)
        }

        val thresholds = floatArrayOf(18f, 14f, 22f, 10f, 26f, 7f)

        for (n in candidateSizes) {
            val existingCats = detectExistingCats(bitmap, detectedRect, n)
            for (th in thresholds) {
                val grid = extractGridStrictPalette(bitmap, detectedRect, n, th)
                val sol = MeowdokuSolver(grid, existingCats).solve()
                if (sol != null) {
                    return AutoSolveAnalysis(
                        boardRect = detectedRect,
                        gridSize = n,
                        regionGrid = grid,
                        existingCats = existingCats,
                        solution = sol
                    )
                }
            }
        }

        val fallbackN = candidateSizes.firstOrNull() ?: 12
        val fallbackCats = detectExistingCats(bitmap, detectedRect, fallbackN)
        val fallbackGrid = extractGridStrictPalette(bitmap, detectedRect, fallbackN, 18f)
        return AutoSolveAnalysis(
            boardRect = detectedRect,
            gridSize = fallbackN,
            regionGrid = fallbackGrid,
            existingCats = fallbackCats,
            solution = null
        )
    }

    private fun detectExactBoardRect(bitmap: Bitmap, fallback: RectF): RectF {
        val w = bitmap.width
        val h = bitmap.height
        val startY = (h * 0.22f).toInt()
        val endY = (h * 0.84f).toInt()
        val startX = (w * 0.01f).toInt()
        val endX = (w * 0.99f).toInt()

        fun isTilePixel(pixel: Int): Boolean {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            return (maxC - minC) > 14 || maxC < 230
        }

        val minRowHits = (w * 0.74f).toInt()
        var rawTop = -1
        var rawBottom = -1

        for (y in startY until endY) {
            var hits = 0
            for (x in startX until endX step 3) {
                if (isTilePixel(bitmap.getPixel(x, y))) hits += 3
            }
            if (hits >= minRowHits) {
                if (rawTop == -1) rawTop = y
                rawBottom = y
            }
        }

        if (rawTop == -1 || rawBottom - rawTop < w * 0.60f) {
            return RectF(fallback)
        }

        val midY1 = rawTop + (rawBottom - rawTop) / 4
        val midY2 = rawBottom - (rawBottom - rawTop) / 4
        val minColHits = ((midY2 - midY1) * 0.50f).toInt()

        var leftEdge = -1
        var rightEdge = -1
        for (x in startX until endX) {
            var hits = 0
            for (y in midY1..midY2 step 2) {
                if (isTilePixel(bitmap.getPixel(x, y))) hits += 2
            }
            if (hits >= minColHits) {
                if (leftEdge == -1) leftEdge = x
                rightEdge = x
            }
        }

        if (leftEdge == -1 || rightEdge - leftEdge < w * 0.60f) {
            return RectF(fallback)
        }

        val boardWidth = rightEdge - leftEdge
        val exactTop = rawTop
        val exactBottom = rawTop + boardWidth

        return RectF(
            leftEdge.toFloat(),
            exactTop.toFloat(),
            rightEdge.toFloat(),
            exactBottom.toFloat()
        )
    }

    private fun detectGridSizeCandidates(bitmap: Bitmap, rect: RectF): List<Int> {
        val w = rect.width()
        val h = rect.height()
        if (w < 200f || h < 200f) return (12 downTo 5).toList() + listOf(13, 14, 15)

        val scores = HashMap<Int, Float>()
        for (n in 5..15) {
            val cellW = w / n.toFloat()
            var gapBrightnessSum = 0f
            var centerBrightnessSum = 0f
            var samples = 0

            for (i in 1 until n) {
                val seamX = (rect.left + i * cellW).toInt().coerceIn(0, bitmap.width - 1)
                val centerX = (rect.left + (i - 0.5f) * cellW).toInt().coerceIn(0, bitmap.width - 1)
                for (step in 1..5) {
                    val y = (rect.top + (step / 6f) * h).toInt().coerceIn(0, bitmap.height - 1)
                    var maxSeamLum = 0f
                    for (dx in -2..2) {
                        val px = (seamX + dx).coerceIn(0, bitmap.width - 1)
                        val p = bitmap.getPixel(px, y)
                        val lum = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3f
                        if (lum > maxSeamLum) maxSeamLum = lum
                    }
                    val cp = bitmap.getPixel(centerX, y)
                    val centerLum = (Color.red(cp) + Color.green(cp) + Color.blue(cp)) / 3f

                    gapBrightnessSum += maxSeamLum
                    centerBrightnessSum += centerLum
                    samples++
                }
            }
            scores[n] = (gapBrightnessSum - centerBrightnessSum) / max(1, samples)
        }

        return scores.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    private fun detectExistingCats(bitmap: Bitmap, rect: RectF, n: Int): List<Pair<Int, Int>> {
        val cats = mutableListOf<Pair<Int, Int>>()
        val cellW = rect.width() / n.toFloat()
        val cellH = rect.height() / n.toFloat()

        for (r in 0 until n) {
            for (c in 0 until n) {
                val left = rect.left + c * cellW
                val top = rect.top + r * cellH
                var darkPixels = 0
                var totalPixels = 0

                val x1 = (left + cellW * 0.25f).toInt().coerceIn(0, bitmap.width - 1)
                val x2 = (left + cellW * 0.75f).toInt().coerceIn(0, bitmap.width - 1)
                val y1 = (top + cellH * 0.22f).toInt().coerceIn(0, bitmap.height - 1)
                val y2 = (top + cellH * 0.78f).toInt().coerceIn(0, bitmap.height - 1)

                for (y in y1..y2 step 2) {
                    for (x in x1..x2 step 2) {
                        val p = bitmap.getPixel(x, y)
                        val rC = Color.red(p)
                        val gC = Color.green(p)
                        val bC = Color.blue(p)
                        totalPixels++
                        if (rC < 85 && gC < 75 && bC < 75) {
                            darkPixels++
                        }
                    }
                }
                if (totalPixels > 0 && darkPixels.toFloat() / totalPixels > 0.08f) {
                    cats.add(Pair(r, c))
                }
            }
        }
        return cats
    }

    private fun extractGridStrictPalette(
        bitmap: Bitmap,
        boardRect: RectF,
        n: Int,
        threshold: Float
    ): Array<IntArray> {
        val cellW = boardRect.width() / n.toFloat()
        val cellH = boardRect.height() / n.toFloat()

        val cornerOffsets = listOf(
            Pair(0.20f, 0.20f),
            Pair(0.80f, 0.20f),
            Pair(0.20f, 0.80f),
            Pair(0.80f, 0.80f)
        )

        val cellColors = Array(n) { Array(n) { Rgb(0f, 0f, 0f) } }

        for (r in 0 until n) {
            for (c in 0 until n) {
                val cellLeft = boardRect.left + c * cellW
                val cellTop = boardRect.top + r * cellH

                val samples = mutableListOf<Rgb>()
                for ((ox, oy) in cornerOffsets) {
                    val px = (cellLeft + ox * cellW).toInt().coerceIn(0, bitmap.width - 1)
                    val py = (cellTop + oy * cellH).toInt().coerceIn(0, bitmap.height - 1)
                    val p = bitmap.getPixel(px, py)
                    val red = Color.red(p).toFloat()
                    val green = Color.green(p).toFloat()
                    val blue = Color.blue(p).toFloat()

                    if (!(red > 240f && green > 235f && blue > 230f) && !(red < 75f && green < 70f && blue < 70f)) {
                        samples.add(Rgb(red, green, blue))
                    }
                }

                if (samples.isEmpty()) {
                    val p = bitmap.getPixel(
                        (cellLeft + 0.5f * cellW).toInt().coerceIn(0, bitmap.width - 1),
                        (cellTop + 0.5f * cellH).toInt().coerceIn(0, bitmap.height - 1)
                    )
                    cellColors[r][c] = Rgb(Color.red(p).toFloat(), Color.green(p).toFloat(), Color.blue(p).toFloat())
                } else {
                    samples.sortBy { it.r + it.g + it.b }
                    cellColors[r][c] = samples[samples.size / 2]
                }
            }
        }

        val clusters = mutableListOf<Cluster>()
        for (r in 0 until n) {
            for (c in 0 until n) {
                val color = cellColors[r][c]
                var bestCluster: Cluster? = null
                var minD = Float.MAX_VALUE

                for (cl in clusters) {
                    val d = color.distanceTo(cl.center)
                    if (d < minD) {
                        minD = d
                        bestCluster = cl
                    }
                }

                if (bestCluster != null && minD <= threshold) {
                    val cnt = bestCluster.cells.size
                    bestCluster.center = Rgb(
                        (bestCluster.center.r * cnt + color.r) / (cnt + 1),
                        (bestCluster.center.g * cnt + color.g) / (cnt + 1),
                        (bestCluster.center.b * cnt + color.b) / (cnt + 1)
                    )
                    bestCluster.cells.add(Pair(r, c))
                } else {
                    clusters.add(Cluster(mutableListOf(Pair(r, c)), color))
                }
            }
        }

        // Если из-за микро-градиентов кластеров получилось чуть больше N — сливаем самые близкие
        while (clusters.size > n) {
            var mergeA = 0
            var mergeB = 1
            var minD = Float.MAX_VALUE
            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val d = clusters[i].center.distanceTo(clusters[j].center)
                    if (d < minD) {
                        minD = d
                        mergeA = i
                        mergeB = j
                    }
                }
            }
            val c1 = clusters[mergeA]
            val c2 = clusters[mergeB]
            val tot = c1.cells.size + c2.cells.size
            c1.center = Rgb(
                (c1.center.r * c1.cells.size + c2.center.r * c2.cells.size) / tot,
                (c1.center.g * c1.cells.size + c2.center.g * c2.cells.size) / tot,
                (c1.center.b * c1.cells.size + c2.center.b * c2.cells.size) / tot
            )
            c1.cells.addAll(c2.cells)
            clusters.removeAt(mergeB)
        }

        val grid = Array(n) { IntArray(n) }
        for ((idx, cl) in clusters.withIndex()) {
            for ((r, c) in cl.cells) {
                grid[r][c] = idx
            }
        }
        return grid
    }
}
