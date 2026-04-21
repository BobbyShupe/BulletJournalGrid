package com.example.bulletjournalgrid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.OverScroller
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BulletJournalGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var numRows = 5
    private var numCols = 5

    private val cellSizeDp = 25f

    private val rowHeaderHorizontalPaddingDp = 10f
    private val colHeaderPaddingDp = 10f

    private var colHeaderHeightDp = 120f
    private var rowHeaderWidthDp = 90f

    private var bottomInsetPx = 0

    private val gridState = mutableListOf<MutableList<Boolean>>()
    private val colHeaders = mutableListOf<String>()
    private val rowHeaders = mutableListOf<String>()

    private var selectedRow = -1
    private var selectedCol = -1

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 1.8f
    }

    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 1.8f
        strokeCap = Paint.Cap.ROUND
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 45f
        textAlign = Paint.Align.CENTER
    }

    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.FILL
    }

    private val pressPaint = Paint().apply {
        color = Color.parseColor("#60FFFFFF")
        style = Paint.Style.FILL
    }

    // Sticky header background
    private val headerBgPaint = Paint().apply {
        color = Color.parseColor("#000000")
    }

    // Scrolling support
    private var contentScrollX = 0f
    private var contentScrollY = 0f
    private var maxScrollX = 0
    private var maxScrollY = 0

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var isPanning = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var downRow = -1
    private var downCol = -1
    private var downHeaderType = 0

    private var isDragging = false
    private var dragType = 0
    private var draggedIndex = -1

    private var pressedRow = -1
    private var pressedCol = -1

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomInsetPx = insets.bottom
            view.setPadding(0, 0, 0, bottomInsetPx)
            updateScrollLimits()
            view.requestLayout()
            view.invalidate()
            WindowInsetsCompat.CONSUMED
        }

        resetGrid()
    }

    private fun resetGrid() {
        gridState.clear()
        colHeaders.clear()
        rowHeaders.clear()

        repeat(numRows) {
            gridState.add(MutableList(numCols) { false })
            rowHeaders.add("Row ${it + 1}")
        }
        repeat(numCols) {
            colHeaders.add("Col ${it + 1}")
        }
        updateHeaderDimensions()
    }

    private fun updateHeaderDimensions() {
        updateRowHeaderWidth()
        updateColHeaderHeight()
    }

    private fun updateRowHeaderWidth() {
        val bounds = Rect()
        var maxWidthPx = 0f
        for (header in rowHeaders) {
            headerPaint.getTextBounds(header, 0, header.length, bounds)
            maxWidthPx = max(maxWidthPx, bounds.width().toFloat())
        }
        rowHeaderWidthDp = (maxWidthPx / resources.displayMetrics.density) + (rowHeaderHorizontalPaddingDp * 2)
        rowHeaderWidthDp = max(rowHeaderWidthDp, 90f)
    }

    private fun updateColHeaderHeight() {
        val bounds = Rect()
        var maxTextWidthPx = 0f
        for (header in colHeaders) {
            headerPaint.getTextBounds(header, 0, header.length, bounds)
            maxTextWidthPx = max(maxTextWidthPx, bounds.width().toFloat())
        }
        colHeaderHeightDp = (maxTextWidthPx / resources.displayMetrics.density) + (colHeaderPaddingDp * 2)
        colHeaderHeightDp = max(colHeaderHeightDp, 120f)
    }

    private fun updateScrollLimits() {
        val d = resources.displayMetrics.density
        val usableHeight = height.toFloat() - bottomInsetPx
        val visibleGridWidth = width.toFloat() - (rowHeaderWidthDp * d)
        val visibleGridHeight = usableHeight - (colHeaderHeightDp * d)
        val totalGridWidth = numCols * cellSizeDp * d
        val totalGridHeight = numRows * cellSizeDp * d

        maxScrollX = (totalGridWidth - visibleGridWidth).coerceAtLeast(0f).toInt()
        maxScrollY = (totalGridHeight - visibleGridHeight).coerceAtLeast(0f).toInt()

        // Clamp current scroll position
        contentScrollX = contentScrollX.coerceIn(0f, maxScrollX.toFloat())
        contentScrollY = contentScrollY.coerceIn(0f, maxScrollY.toFloat())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val d = resources.displayMetrics.density
        val fullContentWidth = (rowHeaderWidthDp + numCols * cellSizeDp) * d
        val fullContentHeight = (colHeaderHeightDp + numRows * cellSizeDp) * d

        val measuredWidth = if (widthMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            fullContentWidth.toInt()
        }
        val measuredHeight = if (heightMode == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            fullContentHeight.toInt()
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
        updateScrollLimits()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScrollLimits()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val ch = colHeaderHeightDp * d
        val rw = rowHeaderWidthDp * d
        val safeHeight = (height.toFloat() - bottomInsetPx) - 0f

        // Background only up to the safe area (prevents drawing under system navigation)
        canvas.drawRect(0f, 0f, width.toFloat(), safeHeight, Paint().apply { color = Color.parseColor("#000000") })

        // Sticky header backgrounds
        canvas.drawRect(rw, 0f, width.toFloat(), ch, headerBgPaint)           // top
        canvas.drawRect(0f, ch, rw, safeHeight, headerBgPaint)               // left

        // 1. Fixed ROW headers (left column)
        canvas.save()
        canvas.clipRect(0f, ch, rw, safeHeight)
        for (r in 0 until numRows) {
            val baseY = ch + r * cs - contentScrollY
            if (baseY + cs < ch || baseY > safeHeight) continue

            val textY = baseY + cs / 2 + headerPaint.textSize / 3
            canvas.drawText(rowHeaders[r], rw / 2f, textY, headerPaint)
        }
        canvas.restore()

        // 2. Fixed COLUMN headers (top row)
        canvas.save()
        canvas.clipRect(rw, 0f, width.toFloat(), ch)
        for (c in 0 until numCols) {
            val baseX = rw + c * cs - contentScrollX + cs / 2
            val cy = ch / 2f

            if (baseX + cs / 2 < rw || baseX - cs / 2 > width.toFloat()) continue

            canvas.save()
            canvas.rotate(-90f, baseX, cy)
            canvas.drawText(colHeaders[c], baseX, cy + headerPaint.textSize / 3, headerPaint)
            canvas.restore()
        }
        canvas.restore()

        // 3. Main scrollable grid content
        canvas.save()
        canvas.clipRect(rw, ch, width.toFloat(), safeHeight)
        canvas.translate(rw - contentScrollX, ch - contentScrollY)

        for (c in 0..numCols) {
            val x = c * cs
            canvas.drawLine(x, 0f, x, numRows * cs, borderPaint)
        }
        for (r in 0..numRows) {
            val y = r * cs
            canvas.drawLine(0f, y, numCols * cs, y, borderPaint)
        }

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                if (gridState[r][c]) {
                    val left = c * cs
                    val top = r * cs
                    canvas.drawLine(left, top, left + cs, top + cs, xPaint)
                    canvas.drawLine(left, top + cs, left + cs, top, xPaint)
                }
            }
        }
        canvas.restore()

        // 4. Selection & press highlights
        if (selectedRow >= 0) {
            val top = ch + selectedRow * cs - contentScrollY
            val bottom = (top + cs).coerceAtMost(safeHeight)
            if (top < safeHeight && bottom > ch) {
                canvas.drawRect(rw, top.coerceAtLeast(ch), width.toFloat(), bottom, selectionPaint)
            }
        }
        if (selectedCol >= 0) {
            val left = rw + selectedCol * cs - contentScrollX
            val right = left + cs
            canvas.drawRect(
                left.coerceAtLeast(rw),
                ch,
                right.coerceAtMost(width.toFloat()),
                safeHeight,
                selectionPaint
            )
        }

        if (pressedRow >= 0 && pressedCol >= 0) {
            val left = rw + pressedCol * cs - contentScrollX
            val top = ch + pressedRow * cs - contentScrollY
            val right = left + cs
            val bottom = top + cs
            if (left < width && top < safeHeight && right > rw && bottom > ch) {
                canvas.drawRect(
                    left.coerceAtLeast(rw),
                    top.coerceAtLeast(ch),
                    right.coerceAtMost(width.toFloat()),
                    bottom.coerceAtMost(safeHeight),
                    pressPaint
                )
            }
        }

        // 5. Top-left corner
        canvas.drawRect(0f, 0f, rw, ch, headerBgPaint)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            contentScrollX = scroller.currX.toFloat()
            contentScrollY = scroller.currY.toFloat()
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val ch = colHeaderHeightDp * d
        val rw = rowHeaderWidthDp * d

        val touchX = event.x
        val touchY = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = touchX
                startY = touchY
                lastTouchX = touchX
                lastTouchY = touchY
                downTime = System.currentTimeMillis()

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                pressedRow = -1
                pressedCol = -1
                isDragging = false
                isPanning = false
                downHeaderType = 0

                val safeHeight = height.toFloat() - bottomInsetPx

                if (touchX >= rw && touchY >= ch && touchY < safeHeight) {
                    val contentX = touchX - rw + contentScrollX
                    val contentY = touchY - ch + contentScrollY
                    downCol = (contentX / cs).toInt().coerceIn(0, numCols - 1)
                    downRow = (contentY / cs).toInt().coerceIn(0, numRows - 1)
                    pressedRow = downRow
                    pressedCol = downCol
                } else if (touchY < ch && touchX >= rw) {
                    val contentX = touchX - rw + contentScrollX
                    downCol = (contentX / cs).toInt().coerceIn(0, numCols - 1)
                    downHeaderType = 2
                } else if (touchX < rw && touchY >= ch && touchY < safeHeight) {
                    val contentY = touchY - ch + contentScrollY
                    downRow = (contentY / cs).toInt().coerceIn(0, numRows - 1)
                    downHeaderType = 1
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)

                val dx = touchX - lastTouchX
                val dy = touchY - lastTouchY
                lastTouchX = touchX
                lastTouchY = touchY

                if (isPanning) {
                    contentScrollX = (contentScrollX - dx).coerceIn(0f, maxScrollX.toFloat())
                    contentScrollY = (contentScrollY - dy).coerceIn(0f, maxScrollY.toFloat())
                    invalidate()
                    return true
                }

                if (isDragging) {
                    handleDrag(touchX, touchY, ch, cs)
                    return true
                }

                val moved = abs(touchX - startX) > touchSlop || abs(touchY - startY) > touchSlop

                if (downHeaderType != 0 && moved) {
                    val isSelected = if (downHeaderType == 1) downRow == selectedRow else downCol == selectedCol
                    if (isSelected) {
                        isDragging = true
                        dragType = downHeaderType
                        draggedIndex = if (dragType == 1) downRow else downCol
                        parent.requestDisallowInterceptTouchEvent(true)
                        handleDrag(touchX, touchY, ch, cs)
                    } else {
                        cleanupTouch()
                        return false
                    }
                } else if (downRow >= 0 && downCol >= 0 && moved) {
                    isPanning = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    contentScrollX = (contentScrollX - dx).coerceIn(0f, maxScrollX.toFloat())
                    contentScrollY = (contentScrollY - dy).coerceIn(0f, maxScrollY.toFloat())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val duration = System.currentTimeMillis() - downTime
                val moved = abs(touchX - startX) > touchSlop || abs(touchY - startY) > touchSlop

                if (isPanning) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velX = velocityTracker?.xVelocity ?: 0f
                    val velY = velocityTracker?.yVelocity ?: 0f

                    scroller.fling(
                        contentScrollX.toInt(),
                        contentScrollY.toInt(),
                        (-velX).toInt(),
                        (-velY).toInt(),
                        0, maxScrollX,
                        0, maxScrollY
                    )
                    ViewCompat.postInvalidateOnAnimation(this)
                    isPanning = false
                } else if (!isDragging) {
                    when {
                        downHeaderType != 0 && duration >= longPressTimeout -> {
                            showHeaderMenu(downHeaderType == 2, if (downHeaderType == 1) downRow else downCol)
                        }
                        downHeaderType == 1 && !moved -> {
                            selectedRow = if (downRow == selectedRow) -1 else downRow
                            selectedCol = -1
                        }
                        downHeaderType == 2 && !moved -> {
                            selectedCol = if (downCol == selectedCol) -1 else downCol
                            selectedRow = -1
                        }
                        downRow >= 0 && downCol >= 0 && !moved -> {
                            gridState[downRow][downCol] = !gridState[downRow][downCol]
                            selectedRow = -1
                            selectedCol = -1
                        }
                        else -> {
                            selectedRow = -1
                            selectedCol = -1
                        }
                    }
                }

                velocityTracker?.recycle()
                velocityTracker = null
                cleanupTouch()
                return true
            }
        }
        return false
    }

    private fun handleDrag(touchX: Float, touchY: Float, ch: Float, cs: Float) {
        val rw = rowHeaderWidthDp * resources.displayMetrics.density

        val target = if (dragType == 1) {
            val contentTouchY = touchY - ch + contentScrollY
            (contentTouchY / cs).toInt().coerceIn(0, numRows - 1)
        } else {
            val contentTouchX = touchX - rw + contentScrollX
            (contentTouchX / cs).toInt().coerceIn(0, numCols - 1)
        }

        if (target != draggedIndex) {
            if (dragType == 1) swapRows(draggedIndex, target)
            else swapColumns(draggedIndex, target)
            draggedIndex = target

            if (dragType == 1) selectedRow = target
            else selectedCol = target

            invalidate()
        }
    }

    private fun cleanupTouch() {
        isDragging = false
        isPanning = false
        dragType = 0
        draggedIndex = -1
        pressedRow = -1
        pressedCol = -1
        downRow = -1
        downCol = -1
        downHeaderType = 0
        invalidate()
    }

    private fun swapRows(from: Int, to: Int) {
        if (from == to) return
        gridState.add(to, gridState.removeAt(from))
        rowHeaders.add(to, rowHeaders.removeAt(from))
        refreshHeaderDimensions()
    }

    private fun swapColumns(from: Int, to: Int) {
        if (from == to) return
        for (row in gridState) {
            row.add(to, row.removeAt(from))
        }
        colHeaders.add(to, colHeaders.removeAt(from))
        refreshHeaderDimensions()
    }

    private fun showHeaderMenu(isColumn: Boolean, index: Int) {
        val title = if (isColumn) "Column: ${colHeaders[index]}" else "Row: ${rowHeaders[index]}"
        val items = arrayOf("Rename", "Delete")

        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameDialog(isColumn, index)
                    1 -> if (isColumn) deleteColumnAt(index) else deleteRowAt(index)
                }
            }
            .show()
    }

    private fun showRenameDialog(isColumn: Boolean, index: Int) {
        val currentName = if (isColumn) colHeaders[index] else rowHeaders[index]

        val input = EditText(context).apply {
            setText(currentName)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#000000"))
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(context)
            .setTitle(if (isColumn) "Rename Column" else "Rename Row")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (isColumn) colHeaders[index] = newName
                    else rowHeaders[index] = newName
                    refreshHeaderDimensions()
                    invalidate()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun addRow() = insertRowAt(numRows)

    fun addColumn() = insertColumnAt(numCols)

    private fun insertRowAt(position: Int) {
        numRows++
        gridState.add(position, MutableList(numCols) { false })
        rowHeaders.add(position, getTodayDateString())
        if (selectedRow >= position) selectedRow++
        refreshHeaderDimensions()
        // NEW: automatically scroll to bottom when a new row is added
        contentScrollY = maxScrollY.toFloat()
        invalidate()
    }

    private fun deleteRowAt(position: Int) {
        if (numRows <= 1) return
        numRows--
        gridState.removeAt(position)
        rowHeaders.removeAt(position)
        if (selectedRow == position) selectedRow = -1
        else if (selectedRow > position) selectedRow--
        refreshHeaderDimensions()
    }

    private fun insertColumnAt(position: Int) {
        numCols++
        gridState.forEach { it.add(position, false) }
        colHeaders.add(position, "Col ${position + 1}")
        if (selectedCol >= position) selectedCol++
        refreshHeaderDimensions()
    }

    private fun deleteColumnAt(position: Int) {
        if (numCols <= 1) return
        numCols--
        gridState.forEach { it.removeAt(position) }
        colHeaders.removeAt(position)
        if (selectedCol == position) selectedCol = -1
        else if (selectedCol > position) selectedCol--
        refreshHeaderDimensions()
    }

    fun getCurrentGridData(): GridData {
        return GridData(
            name = "Unnamed",
            numRows = numRows,
            numCols = numCols,
            gridState = gridState.map { it.toList() },
            colHeaders = colHeaders.toList(),
            rowHeaders = rowHeaders.toList()
        )
    }

    fun loadGridData(data: GridData) {
        numRows = data.numRows
        numCols = data.numCols

        gridState.clear()
        if (data.gridState.isNotEmpty()) {
            data.gridState.forEach { row -> gridState.add(row.toMutableList()) }
        } else {
            repeat(numRows) { gridState.add(MutableList(numCols) { false }) }
        }

        colHeaders.clear()
        colHeaders.addAll(if (data.colHeaders.isNotEmpty()) data.colHeaders else List(numCols) { "Col ${it + 1}" })

        rowHeaders.clear()
        rowHeaders.addAll(if (data.rowHeaders.isNotEmpty()) data.rowHeaders else List(numRows) { "Row ${it + 1}" })

        selectedRow = -1
        selectedCol = -1
        contentScrollX = 0f

        refreshHeaderDimensions()

        // NEW: automatically scroll to bottom on load (new grid, switch grid, etc.)
        contentScrollY = maxScrollY.toFloat()
        invalidate()
    }

    private fun refreshHeaderDimensions() {
        updateHeaderDimensions()
        updateScrollLimits()
        requestLayout()
        invalidate()
    }

    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("MM-dd-YYYY", Locale.getDefault())
        return formatter.format(Date())
    }
}