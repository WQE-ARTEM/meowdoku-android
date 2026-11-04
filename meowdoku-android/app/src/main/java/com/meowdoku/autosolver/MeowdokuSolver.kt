package com.meowdoku.autosolver

import kotlin.math.abs

class MeowdokuSolver(private val grid: Array<IntArray>) {
    private val n: Int = grid.size
    private val regions: Map<Int, List<Pair<Int, Int>>>
    private val occupiedRows = BooleanArray(n)
    private val occupiedCols = BooleanArray(n)
    private val occupiedRegions = mutableSetOf<Int>()
    private val cats = mutableListOf<Pair<Int, Int>>()
    private val blockedCount = Array(n) { IntArray(n) }

    init {
        val regMap = mutableMapOf<Int, MutableList<Pair<Int, Int>>>()
        for (r in 0 until n) {
            for (c in 0 until n) {
                regMap.getOrPut(grid[r][c]) { mutableListOf() }.add(Pair(r, c))
            }
        }
        regions = regMap
    }

    fun solve(): List<Pair<Int, Int>>? {
        if (n < 4 || regions.size != n) return null
        if (backtrack()) {
            return cats.toList()
        }
        return null
    }

    private fun isSafe(r: Int, c: Int, regId: Int): Boolean {
        if (occupiedRows[r] || occupiedCols[c] || occupiedRegions.contains(regId)) {
            return false
        }
        for ((cr, cc) in cats) {
            if (abs(cr - r) <= 1 && abs(cc - c) <= 1) {
                return false
            }
        }
        return true
    }

    private fun getAffectedCells(r: Int, c: Int, regId: Int): List<Pair<Int, Int>> {
        val set = HashSet<Pair<Int, Int>>()
        for (i in 0 until n) {
            set.add(Pair(r, i))
            set.add(Pair(i, c))
        }
        regions[regId]?.let { set.addAll(it) }
        for (dr in -1..1) {
            for (dc in -1..1) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until n && nc in 0 until n) {
                    set.add(Pair(nr, nc))
                }
            }
        }
        return set.toList()
    }

    private fun selectMrvCandidates(): List<Pair<Int, Int>>? {
        var best: List<Pair<Int, Int>>? = null
        var minLen = n + 1

        // 1. Проверка регионов
        for ((regId, cells) in regions) {
            if (occupiedRegions.contains(regId)) continue
            val avail = cells.filter { (r, c) -> blockedCount[r][c] == 0 && isSafe(r, c, regId) }
            if (avail.isEmpty()) return null
            if (avail.size < minLen) {
                minLen = avail.size
                best = avail
            }
        }

        // 2. Проверка строк
        for (r in 0 until n) {
            if (occupiedRows[r]) continue
            val avail = (0 until n)
                .filter { c -> blockedCount[r][c] == 0 && isSafe(r, c, grid[r][c]) }
                .map { c -> Pair(r, c) }
            if (avail.isEmpty()) return null
            if (avail.size < minLen) {
                minLen = avail.size
                best = avail
            }
        }

        // 3. Проверка столбцов
        for (c in 0 until n) {
            if (occupiedCols[c]) continue
            val avail = (0 until n)
                .filter { r -> blockedCount[r][c] == 0 && isSafe(r, c, grid[r][c]) }
                .map { r -> Pair(r, c) }
            if (avail.isEmpty()) return null
            if (avail.size < minLen) {
                minLen = avail.size
                best = avail
            }
        }

        return best
    }

    private fun backtrack(): Boolean {
        if (cats.size == n) return true
        val candidates = selectMrvCandidates() ?: return false

        for ((r, c) in candidates) {
            val regId = grid[r][c]
            if (!isSafe(r, c, regId)) continue

            cats.add(Pair(r, c))
            occupiedRows[r] = true
            occupiedCols[c] = true
            occupiedRegions.add(regId)

            val affected = getAffectedCells(r, c, regId)
            for ((ar, ac) in affected) blockedCount[ar][ac]++

            if (backtrack()) return true

            for ((ar, ac) in affected) blockedCount[ar][ac]--
            occupiedRegions.remove(regId)
            occupiedCols[c] = false
            occupiedRows[r] = false
            cats.removeAt(cats.lastIndex)
        }
        return false
    }
}
