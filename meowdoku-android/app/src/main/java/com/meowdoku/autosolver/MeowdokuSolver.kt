package com.meowdoku.autosolver

import kotlin.math.abs

class MeowdokuSolver(
    private val grid: Array<IntArray>,
    private val prePlacedCats: List<Pair<Int, Int>> = emptyList()
) {
    private val n = grid.size

    fun solve(): List<Pair<Int, Int>>? {
        val regionCells = HashMap<Int, MutableList<Pair<Int, Int>>>()
        for (r in 0 until n) {
            for (c in 0 until n) {
                val reg = grid[r][c]
                if (reg < 0) return null
                regionCells.getOrPut(reg) { mutableListOf() }.add(Pair(r, c))
            }
        }

        if (regionCells.size != n) return null

        val usedRows = BooleanArray(n)
        val usedCols = BooleanArray(n)
        val usedRegions = HashSet<Int>()
        val assigned = ArrayList<Pair<Int, Int>>(n)

        for ((r, c) in prePlacedCats) {
            if (r !in 0 until n || c !in 0 until n) continue
            val reg = grid[r][c]
            if (usedRows[r] || usedCols[c] || usedRegions.contains(reg)) {
                return if (prePlacedCats.isNotEmpty()) MeowdokuSolver(grid, emptyList()).solve() else null
            }
            for ((ar, ac) in assigned) {
                if (abs(ar - r) <= 1 && abs(ac - c) <= 1) {
                    return if (prePlacedCats.isNotEmpty()) MeowdokuSolver(grid, emptyList()).solve() else null
                }
            }
            usedRows[r] = true
            usedCols[c] = true
            usedRegions.add(reg)
            assigned.add(Pair(r, c))
        }

        val remainingRegions = regionCells.keys
            .filter { !usedRegions.contains(it) }
            .sortedBy { regionCells[it]?.size ?: 999 }

        fun canPlace(r: Int, c: Int): Boolean {
            if (usedRows[r] || usedCols[c]) return false
            for (i in 0 until assigned.size) {
                val ar = assigned[i].first
                val ac = assigned[i].second
                // Жесткий запрет на касания по вертикали, горизонтали и диагонали
                if (abs(ar - r) <= 1 && abs(ac - c) <= 1) return false
            }
            return true
        }

        fun backtrack(idx: Int): Boolean {
            if (idx == remainingRegions.size) return true
            val reg = remainingRegions[idx]
            val cells = regionCells[reg] ?: return false

            for (i in 0 until cells.size) {
                val (r, c) = cells[i]
                if (canPlace(r, c)) {
                    usedRows[r] = true
                    usedCols[c] = true
                    assigned.add(Pair(r, c))

                    if (backtrack(idx + 1)) return true

                    assigned.removeAt(assigned.size - 1)
                    usedRows[r] = false
                    usedCols[c] = false
                }
            }
            return false
        }

        if (backtrack(0)) return assigned
        if (prePlacedCats.isNotEmpty()) {
            return MeowdokuSolver(grid, emptyList()).solve()
        }
        return null
    }
}
