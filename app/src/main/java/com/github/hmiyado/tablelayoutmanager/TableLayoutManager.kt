package com.github.hmiyado.tablelayoutmanager

import android.content.Context
import android.support.v7.widget.OrientationHelper
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.util.TypedValue
import android.view.View

/**
 * Anchor は1個
 * 1個の Anchor を基準に，縦横にレイアウトしていく
 * とりあえず無限スクロールはなくてもいいかな？
 *
 * LinearLayoutManager に比べて考慮していない事項
 * - stackFromEnd 常に左上からレイアウトするため．
 */
class TableLayoutManager(
    private val context: Context,
    private val row: Int,
    private val column: Int
) : RecyclerView.LayoutManager() {
    private var layoutState: LayoutState = LayoutState()
    private val orientationHelperVector = Vector2(
        OrientationHelper.createVerticalHelper(this),
        OrientationHelper.createHorizontalHelper(this)
    )
    private val anchorInfo: AnchorInfo = AnchorInfo(orientationHelperVector)

    init {
        Log.d("TableLayoutManager#constructor", "(row, column)=($row, $column)")
    }

    private fun coordinateToPosition(x: Int, y: Int): Int {
        if (x !in 0 until row || y !in 0 until column) {
            throw IndexOutOfBoundsException("(row, column)=($row, $column), (x, y)=($x, $y)")
        }
        return y * row + x
    }

    private fun positionToCoordinate(position: Int): Vector2<Int> {
        return Vector2(
            position % row,
            position / row
        )
    }

    override fun canScrollHorizontally(): Boolean {
        return true
    }

    override fun canScrollVertically(): Boolean {
        return true
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            96f,
            context.resources.displayMetrics
        ).toInt()
        return RecyclerView.LayoutParams(size, size)
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        recycler ?: return
        state ?: return

        findAndUpdateAnchor(recycler, state)
        Log.d("AnchorInfo", "${anchorInfo.position} ${anchorInfo.coordinate}")
        // todo 画面外に余分に描画する

        detachAndScrapAttachedViews(recycler)
        layoutState.isPreLayout = state.isPreLayout
        updateLayoutStateToFillEndEnd(anchorInfo.position, anchorInfo.coordinate)
        fill(recycler, state, layoutState)
        updateLayoutStateToFillStartEnd(anchorInfo.position, anchorInfo.coordinate)
        fill(recycler, state, layoutState)
        updateLayoutStateToFillEndStart(anchorInfo.position, anchorInfo.coordinate)
        fill(recycler, state, layoutState)
        updateLayoutStateToFillStartStart(anchorInfo.position, anchorInfo.coordinate)
        fill(recycler, state, layoutState)

        if (!state.isPreLayout) {
            orientationHelperVector.map { it.onLayoutComplete() }
        } else {
            anchorInfo.reset()
        }
    }

    private fun fill(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        layoutState: LayoutState
    ): Vector2<Int> {
        val startAvailablePixel = layoutState.availablePixels
        val initialOffset = layoutState.offset
        val (startRow, startColumn) = positionToCoordinate(layoutState.currentPosition)
        val layoutChunkResult = LayoutChunkResult()
        var remainingSpace = layoutState.availablePixels
        var top = 0
        var bottom = 0
        var isInitializedTopBottom = false
        while (remainingSpace.fold { x, y -> x > 0 || y > 0 }) {
            layoutChunkResult.reset()

            // view を探してきてレイアウトする
            val view = recycler.getViewForPosition(layoutState.currentPosition)
            if (view == null) {
                layoutChunkResult.finished = true
            } else {
                addView(view)
                measureChildWithMargins(view, 0, 0)
                layoutChunkResult.consumedPixels =
                        orientationHelperVector.map { it.getDecoratedMeasurement(view) }

                val (left, right) = when (layoutState.layoutDirection.x) {
                    TableLayoutManager.LayoutState.LayoutDirection.START -> {
                        layoutState.offset.x - layoutChunkResult.consumedPixels.x to layoutState.offset.x
                    }
                    TableLayoutManager.LayoutState.LayoutDirection.END   -> {
                        layoutState.offset.x to layoutState.offset.x + layoutChunkResult.consumedPixels.x
                    }
                }
                if (!isInitializedTopBottom) {
                    val topBottom = when (layoutState.layoutDirection.y) {
                        TableLayoutManager.LayoutState.LayoutDirection.START -> {
                            layoutState.offset.y - layoutChunkResult.consumedPixels.y to layoutState.offset.y
                        }
                        TableLayoutManager.LayoutState.LayoutDirection.END   -> {
                            layoutState.offset.y to layoutState.offset.y + layoutChunkResult.consumedPixels.y
                        }
                    }
                    top = topBottom.first
                    bottom = topBottom.second
                    isInitializedTopBottom = true
                }

                layoutDecoratedWithMargins(view, left, top, right, bottom)
                view.layoutParams.let { it as RecyclerView.LayoutParams }.let {
                    if (it.isItemRemoved || it.isItemChanged) {
                        layoutChunkResult.ignoredConsumed = true
                    }
                }
                layoutChunkResult.focusable = view.hasFocusable()
            }

            if (layoutChunkResult.finished) {
                break
            }

            // 次のレイアウトのための準備
            layoutState.offset = Vector2(
                layoutState.offset.x + layoutChunkResult.consumedPixels.x * layoutState.layoutDirection.x.diff,
                layoutState.offset.y
            )

            if (!layoutChunkResult.ignoredConsumed || !state.isPreLayout) {
                remainingSpace = Vector2(
                    remainingSpace.x - layoutChunkResult.consumedPixels.x,
                    remainingSpace.y
                )
                if (remainingSpace.x <= 0 && remainingSpace.y > 0) {
                    layoutState.availablePixels = Vector2(
                        startAvailablePixel.x,
                        layoutState.availablePixels.y - layoutChunkResult.consumedPixels.y
                    )
                    remainingSpace = Vector2(
                        layoutState.availablePixels.x,
                        remainingSpace.y - layoutChunkResult.consumedPixels.y
                    )
                    val newColumn =
                        positionToCoordinate(layoutState.currentPosition).y + layoutState.layoutDirection.y.diff
                    val oldPosition = layoutState.currentPosition
                    layoutState.currentPosition = coordinateToPosition(
                        startRow,
                        when {
                            newColumn < 0               -> column + newColumn
                            newColumn in 0 until column -> newColumn
                            newColumn >= column         -> newColumn - column
                            else                        -> throw NotImplementedError()
                        }
                    )
                    Log.d(
                        "LayoutState#newColumn",
                        "position $oldPosition -> ${layoutState.currentPosition} (${positionToCoordinate(
                            oldPosition
                        )} -> ${positionToCoordinate(layoutState.currentPosition)})"
                    )
                    layoutState.offset = Vector2(
                        initialOffset.x,
                        layoutState.offset.y + layoutChunkResult.consumedPixels.y * layoutState.layoutDirection.y.diff
                    )
                    val topBottom = when (layoutState.layoutDirection.y) {
                        TableLayoutManager.LayoutState.LayoutDirection.START -> {
                            layoutState.offset.y - layoutChunkResult.consumedPixels.y to layoutState.offset.y
                        }
                        TableLayoutManager.LayoutState.LayoutDirection.END   -> {
                            layoutState.offset.y to layoutState.offset.y + layoutChunkResult.consumedPixels.y
                        }
                    }
                    top = topBottom.first
                    bottom = topBottom.second
                } else {
                    layoutState.availablePixels = layoutState.availablePixels
                        .copy(x = layoutState.availablePixels.x - layoutChunkResult.consumedPixels.x)

                    val newRow =
                        positionToCoordinate(layoutState.currentPosition).x + layoutState.layoutDirection.x.diff
                    val oldPosition = layoutState.currentPosition
                    layoutState.currentPosition = coordinateToPosition(
                        when {
                            newRow < 0            -> row + newRow
                            newRow in 0 until row -> newRow
                            newRow >= row         -> newRow - row
                            else                  -> throw NotImplementedError()
                        },
                        positionToCoordinate(layoutState.currentPosition).y
                    )
                    Log.d(
                        "LayoutState#sameColumn",
                        "position $oldPosition -> ${layoutState.currentPosition} (${positionToCoordinate(
                            oldPosition
                        )} -> ${positionToCoordinate(layoutState.currentPosition)})"
                    )
                }
            }
        }
        return startAvailablePixel.apply(layoutState.availablePixels) { start, available -> start - available }

    }

    private fun updateLayoutStateToFillStartEnd(itemPosition: Int, offset: Vector2<Int>) {
        Log.d("updateLayoutState", "StartEnd")

        layoutState.availablePixels = Vector2(
            offset.x - orientationHelperVector.x.startAfterPadding,
            orientationHelperVector.y.endAfterPadding - offset.y
        )
        layoutState.itemDirection = LayoutState.ItemDirection.TAIL
        layoutState.currentPosition = itemPosition
        layoutState.layoutDirection =
                Vector2(LayoutState.LayoutDirection.START, LayoutState.LayoutDirection.END)
        layoutState.offset = offset
        layoutState.scrollingOffset = Vector2(0, 0)
    }

    private fun updateLayoutStateToFillStartStart(itemPosition: Int, offset: Vector2<Int>) {
        Log.d("updateLayoutState", "StartStart")

        layoutState.availablePixels = Vector2(
            offset.x - orientationHelperVector.x.startAfterPadding,
            offset.y - orientationHelperVector.y.startAfterPadding
        )
        layoutState.itemDirection = LayoutState.ItemDirection.TAIL
        layoutState.currentPosition = itemPosition
        layoutState.layoutDirection =
                Vector2(LayoutState.LayoutDirection.START, LayoutState.LayoutDirection.START)
        layoutState.offset = offset
        layoutState.scrollingOffset = Vector2(0, 0)
    }

    private fun updateLayoutStateToFillEndStart(itemPosition: Int, offset: Vector2<Int>) {
        Log.d("updateLayoutState", "EndStart")

        layoutState.availablePixels = Vector2(
            orientationHelperVector.x.endAfterPadding - offset.x,
            offset.y - orientationHelperVector.y.startAfterPadding
        )
        layoutState.itemDirection = LayoutState.ItemDirection.TAIL
        layoutState.currentPosition = itemPosition
        layoutState.layoutDirection =
                Vector2(LayoutState.LayoutDirection.END, LayoutState.LayoutDirection.START)
        layoutState.offset = offset
        layoutState.scrollingOffset = Vector2(0, 0)
    }

    private fun updateLayoutStateToFillEndEnd(itemPosition: Int, offset: Vector2<Int>) {
        Log.d("updateLayoutState", "EndEnd")
        layoutState.availablePixels = orientationHelperVector.pairwise(offset)
            .map { (helper, offset) -> helper.endAfterPadding - offset }
        layoutState.itemDirection = LayoutState.ItemDirection.TAIL
        layoutState.currentPosition = itemPosition
        layoutState.layoutDirection =
                Vector2(LayoutState.LayoutDirection.END, LayoutState.LayoutDirection.END)
        layoutState.offset = offset
        layoutState.scrollingOffset = Vector2(0, 0)
    }

    private fun findAndUpdateAnchor(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        val focused = focusedChild
        if (anchorInfo.notValid()) {
            anchorInfo.reset()
            updateAnchorInfoForLayout(recycler, state)
            anchorInfo.valid = true
        } else if (focused != null
            && orientationHelperVector.map {
                it.getDecoratedStart(focused) >= it.endAfterPadding
                        || it.getDecoratedEnd(focused) <= it.startAfterPadding
            }.fold { x, y -> x || y }) {
            anchorInfo.assignFromViewAndKeepVisibleRect(
                focused,
                getPosition(focused)
            )
        }
    }

    private fun updateAnchorInfoForLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        if (updateAnchorFromChildren(recycler, state)) {
            return
        }

        anchorInfo.assignCoordinateFromPadding()
        anchorInfo.position = 0
    }

    private fun updateAnchorFromChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Boolean {
        if (childCount == 0) return false
        val focused = focusedChild
        if (focused != null && anchorInfo.isViewValidAsAnchor(focused, state)) {
            anchorInfo.assignFromViewAndKeepVisibleRect(
                focused,
                getPosition(focused)
            )
            return true
        }
        val referenceChild = findReferenceChildClosestToStart(recycler, state)

        if (referenceChild != null) {
            anchorInfo.assignFromView(
                referenceChild,
                getPosition(referenceChild)
            )
            if (!state.isPreLayout && supportsPredictiveItemAnimations()) {
                anchorInfo.coordinate = orientationHelperVector.map {
                    it.getDecoratedStart(referenceChild) >= it.endAfterPadding
                            || it.getDecoratedEnd(referenceChild) < it.startAfterPadding
                }.triplies(anchorInfo.coordinate, orientationHelperVector)
                    .map { (notVisible, coordinate, orientationHelper) ->
                        if (notVisible) {
                            orientationHelper.startAfterPadding
                        } else {
                            coordinate
                        }
                    }
            }
            return true
        }
        return false
    }


    private fun findReferenceChildClosestToStart(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): View? {
        return findFirstReferenceChild(recycler, state)
    }

    private fun findFirstReferenceChild(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): View? {
        return findReferenceChild(recycler, state, 0, childCount, state.itemCount)
    }

    private fun findReferenceChild(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        start: Int,
        end: Int,
        itemCount: Int
    ): View? {
        var invalidMatch: View? = null
        var outOfBoundsMatch: View? = null

        val boundsStart = orientationHelperVector.map { it.startAfterPadding }
        val boundsEnd = orientationHelperVector.map { it.endAfterPadding }
        val diff = if (end > start) 1 else -1

        (start..end step diff).map { getChildAt(it) }.forEach { view ->
            val position = getPosition(view)
            if (position in 0..(itemCount - 1)) {
                if ((view.layoutParams as RecyclerView.LayoutParams).isItemRemoved) {
                    if (invalidMatch == null) {
                        invalidMatch = view // removed item, least preferred
                    }
                } else if (orientationHelperVector.triplies(
                        boundsEnd,
                        boundsStart
                    ).map { (helper, end, start) ->
                        helper.getDecoratedStart(view) >= end
                                || helper.getDecoratedEnd(view) < start
                    }.fold { x, y -> x && y }) {
                    if (outOfBoundsMatch == null) {
                        outOfBoundsMatch = view // item is not visible, less preferred
                    }
                } else {
                    return view
                }
            }
        }
        return outOfBoundsMatch ?: return invalidMatch
    }


    private class LayoutState {
        enum class ItemDirection {
            HEAD, TAIL
        }

        enum class LayoutDirection {
            START, END;

            val diff: Int
                get() = when (this) {
                    TableLayoutManager.LayoutState.LayoutDirection.START -> -1
                    TableLayoutManager.LayoutState.LayoutDirection.END   -> 1
                }
        }

        var availablePixels = Vector2(0, 0)
        var currentPosition = -1
        val isInfinite = true
        var isPreLayout: Boolean = false
        var itemDirection = ItemDirection.TAIL
        var layoutDirection = Vector2(LayoutState.LayoutDirection.END, LayoutDirection.END)
        var offset = Vector2(0, 0)
        var scrollingOffset = Vector2(0, 0)
    }

    private class AnchorInfo(
        val orientationHelperVector: Vector2<OrientationHelper>
    ) {
        var valid = false
        // Anchor の座標
        var coordinate = Vector2(0, 0)
        // Anchor の Adapter 中の位置
        var position = -1

        fun notValid() = !valid

        fun reset() {
            valid = false
        }

        fun assignFromView(child: View, position: Int) {
            coordinate = orientationHelperVector.map { it.getDecoratedStart(child) }

            this.position = position
        }

        fun isViewValidAsAnchor(child: View, state: RecyclerView.State): Boolean {
            val lp = child.layoutParams as RecyclerView.LayoutParams
            return (!lp.isItemRemoved
                    && lp.viewLayoutPosition >= 0
                    && lp.viewLayoutPosition < state.itemCount)
        }

        fun assignFromViewAndKeepVisibleRect(
            child: View,
            position: Int
        ) {
            val spaceChange = orientationHelperVector.map { it.totalSpaceChange }
            if (spaceChange.map { it >= 0 }.fold { x, y -> x && y }) {
                assignFromView(child, position)
                return
            }

            this.position = position
            val childStart = orientationHelperVector.map { it.getDecoratedStart(child) }
            val startMargin =
                childStart.apply(orientationHelperVector.map { it.getStartAfterPadding() }) { a, b -> a - b }
            coordinate = childStart

            startMargin
                .combine(orientationHelperVector, coordinate, childStart, spaceChange)
                .map { (startMargin, orientationHelper, coordinate, childStart, spaceChange) ->
                    if (startMargin > 0) {
                        val estimatedEnd =
                            childStart + orientationHelper.getDecoratedMeasurement(child)
                        val previousLayoutEnd =
                            orientationHelper.endAfterPadding - spaceChange
                        val previousEndMargin =
                            previousLayoutEnd - orientationHelper.getDecoratedEnd(child)
                        val endReference = orientationHelper.endAfterPadding - Math.min(
                            0,
                            previousEndMargin
                        )
                        val endMargin = endReference - estimatedEnd
                        if (endMargin < 0) {
                            return@map coordinate - Math.min(startMargin, -endMargin)
                        }
                    }
                    coordinate
                }
        }

        fun assignCoordinateFromPadding() {
            coordinate = orientationHelperVector.map { it.startAfterPadding }
        }
    }

    private data class LayoutChunkResult(
        var consumedPixels: Vector2<Int> = Vector2(0, 0),
        var finished: Boolean = false,
        var ignoredConsumed: Boolean = false,
        var focusable: Boolean = false
    ) {
        fun reset() {
            consumedPixels = Vector2(0, 0)
            finished = false
            ignoredConsumed = false
            focusable = false
        }
    }


    private data class Vector2<T>(val x: T, val y: T) {
        fun <U> map(mapper: (T) -> U): Vector2<U> = Vector2(mapper(x), mapper(y))

        fun <U> apply(other: Vector2<T>, applier: (T, T) -> U) =
            Vector2(applier(x, other.x), applier(y, other.y))

        fun <U> fold(folder: (T, T) -> U) = folder(x, y)

        fun <U> pairwise(other: Vector2<U>) = Vector2(x to other.x, y to other.y)

        fun <U, V> triplies(other1: Vector2<U>, other2: Vector2<V>) =
            Vector2(Triple(x, other1.x, other2.x), Triple(y, other1.y, other2.y))

        fun <T1, T2, T3, T4> combine(
            o1: Vector2<T1>,
            o2: Vector2<T2>,
            o3: Vector2<T3>,
            o4: Vector2<T4>
        ) = Vector2(Box5(x, o1.x, o2.x, o3.x, o4.x), Box5(y, o1.y, o2.y, o3.y, o4.y))

        fun <T1, T2, T3, T4, T5> combine(
            o1: Vector2<T1>,
            o2: Vector2<T2>,
            o3: Vector2<T3>,
            o4: Vector2<T4>,
            o5: Vector2<T5>
        ) = Vector2(Box6(x, o1.x, o2.x, o3.x, o4.x, o5.x), Box6(y, o1.y, o2.y, o3.y, o4.y, o5.y))

        data class Box5<T1, T2, T3, T4, T5>(
            val a1: T1,
            val a2: T2,
            val a3: T3,
            val a4: T4,
            val a5: T5
        )

        data class Box6<T1, T2, T3, T4, T5, T6>(
            val a1: T1,
            val a2: T2,
            val a3: T3,
            val a4: T4,
            val a5: T5,
            val a6: T6
        )

        companion object {
            val Empty = Vector2(null, null)
        }
    }

    private operator fun Vector2<Int>.minus(other: Vector2<Int>) = Vector2(x - other.x, y - other.y)
}