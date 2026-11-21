package com.myfun.funclass.core.model

/**
 * Computed horizontal placement for a course block within a single day column.
 *
 * - Non-overlapping course: [columnIndex] = 0, [columnCount] = 1, [isFolded] = false.
 * - 2 concurrent overlapping courses: side-by-side with [columnCount] = 2 and [conflictPeers] populated.
 * - 3+ concurrent overlapping courses: folded into a single representative block with [isFolded] = true
 *   and all overlapping items in [foldedItems], so narrow mobile columns stay readable and tapping opens a picker.
 */
data class PlacedCourseBlock(
    val primary: ResolvedCourseItem,
    val startNode: Int,
    val endNode: Int,
    val columnIndex: Int,
    val columnCount: Int,
    val isFolded: Boolean = false,
    val foldedItems: List<ResolvedCourseItem> = emptyList(),
    val conflictPeers: List<ResolvedCourseItem> = emptyList()
) {
    val nodeSpan: Int get() = (endNode - startNode + 1).coerceAtLeast(1)
    val extraFoldedCount: Int get() = (foldedItems.size - 1).coerceAtLeast(0)
}

object CourseOverlapLayout {

    /**
     * Computes side-by-side / folded placement blocks for all courses in a single day column.
     *
     * @param maxSideBySideColumns maximum number of concurrent courses rendered side-by-side before folding (default 2).
     */
    fun layoutDay(
        items: List<ResolvedCourseItem>,
        maxSideBySideColumns: Int = 2
    ): List<PlacedCourseBlock> {
        if (items.isEmpty()) return emptyList()

        val sorted = items.sortedWith(
            compareBy<ResolvedCourseItem> { it.session.startNode }
                .thenByDescending { it.session.nodeCount }
                .thenBy { it.course.name }
        )

        // 1. Partition into connected overlap clusters
        val clusters = mutableListOf<MutableList<ResolvedCourseItem>>()
        var currentCluster = mutableListOf<ResolvedCourseItem>()
        var clusterMaxEnd = -1

        for (item in sorted) {
            val s = item.session.startNode
            val e = item.session.endNode
            if (currentCluster.isEmpty()) {
                currentCluster.add(item)
                clusterMaxEnd = e
            } else if (s <= clusterMaxEnd) {
                currentCluster.add(item)
                if (e > clusterMaxEnd) clusterMaxEnd = e
            } else {
                clusters.add(currentCluster)
                currentCluster = mutableListOf(item)
                clusterMaxEnd = e
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        // 2. For each cluster, greedily assign columns to measure max concurrency
        val result = mutableListOf<PlacedCourseBlock>()
        for (cluster in clusters) {
            if (cluster.size == 1) {
                val single = cluster.first()
                result += PlacedCourseBlock(
                    primary = single,
                    startNode = single.session.startNode,
                    endNode = single.session.endNode,
                    columnIndex = 0,
                    columnCount = 1
                )
                continue
            }

            val colEndNodes = mutableListOf<Int>()
            val assignedCols = IntArray(cluster.size)
            for (i in cluster.indices) {
                val item = cluster[i]
                var placedCol = -1
                for (c in colEndNodes.indices) {
                    if (colEndNodes[c] < item.session.startNode) {
                        placedCol = c
                        colEndNodes[c] = item.session.endNode
                        break
                    }
                }
                if (placedCol == -1) {
                    placedCol = colEndNodes.size
                    colEndNodes.add(item.session.endNode)
                }
                assignedCols[i] = placedCol
            }

            val concurrency = colEndNodes.size
            if (concurrency <= maxSideBySideColumns) {
                // Side-by-side placement (e.g., 2 columns)
                for (i in cluster.indices) {
                    val item = cluster[i]
                    val peers = cluster.filterIndexed { j, other ->
                        j != i &&
                            other.session.startNode <= item.session.endNode &&
                            item.session.startNode <= other.session.endNode
                    }
                    result += PlacedCourseBlock(
                        primary = item,
                        startNode = item.session.startNode,
                        endNode = item.session.endNode,
                        columnIndex = assignedCols[i],
                        columnCount = concurrency,
                        isFolded = false,
                        conflictPeers = peers
                    )
                }
            } else {
                // Fold 3+ concurrent overlapping courses into a clean full-width block with +N badge
                val minStart = cluster.minOf { it.session.startNode }
                val maxEnd = cluster.maxOf { it.session.endNode }
                result += PlacedCourseBlock(
                    primary = cluster.first(),
                    startNode = minStart,
                    endNode = maxEnd,
                    columnIndex = 0,
                    columnCount = 1,
                    isFolded = true,
                    foldedItems = cluster.toList(),
                    conflictPeers = cluster.drop(1)
                )
            }
        }

        return result
    }
}
