package com.example.bulletjournalgrid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs
import kotlin.math.max

class BulletJournalGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var numRows = 5
    private var numCols = 5

    private val cellSizeDp = 25f

    // ==================== PADDING (easy to tune) ====================
    private val rowHeaderHorizontalPaddingDp = 10f
    private val colHeaderPaddingDp = 10f          // Main padding for column header height

    private var colHeaderHeightDp = 120f
    private var rowHeaderWidthDp = 90f

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

    // FIXED: Use text WIDTH for column header height (because text is rotated -90°)
    private fun updateColHeaderHeight() {
        val bounds = Rect()
        var maxTextWidthPx = 0f

        for (header in colHeaders) {
            headerPaint.getTextBounds(header, 0, header.length, bounds)
            maxTextWidthPx = max(maxTextWidthPx, bounds.width().toFloat())
        }

        // The rotated text needs vertical space equal to its horizontal width + padding
        colHeaderHeightDp = (maxTextWidthPx / resources.displayMetrics.density) + (colHeaderPaddingDp * 2)
        colHeaderHeightDp = max(colHeaderHeightDp, 120f)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val w = (rowHeaderWidthDp + numCols * cellSizeDp) * d
        val h = (colHeaderHeightDp + numRows * cellSizeDp) * d
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val ch = colHeaderHeightDp * d
        val rw = rowHeaderWidthDp * d

        // Selection & Press
        if (selectedRow >= 0) {
            val top = ch + selectedRow * cs
            canvas.drawRect(0f, top, width.toFloat(), top + cs, selectionPaint)
        }
        if (selectedCol >= 0) {
            val left = rw + selectedCol * cs
            canvas.drawRect(left, 0f, left + cs, height.toFloat(), selectionPaint)
        }

        if (pressedRow >= 0 && pressedCol >= 0) {
            val left = rw + pressedCol * cs
            val top = ch + pressedRow * cs
            canvas.drawRect(left, top, left + cs, top + cs, pressPaint)
        }

        // Grid lines
        for (c in 0..numCols) {
            val x = rw + c * cs
            canvas.drawLine(x, 0f, x, height.toFloat(), borderPaint)
        }
        for (r in 0..numRows) {
            val y = ch + r * cs
            canvas.drawLine(0f, y, width.toFloat(), y, borderPaint)
        }

        // Column headers (rotated)
        for (c in 0 until numCols) {
            val cx = rw + c * cs + cs / 2
            val cy = ch / 2f
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            canvas.drawText(colHeaders[c], cx, cy + headerPaint.textSize / 3, headerPaint)
            canvas.restore()
        }

        // Row headers + cells
        for (r in 0 until numRows) {
            val y = ch + r * cs + cs / 2
            canvas.drawText(rowHeaders[r], rw / 2, y + headerPaint.textSize / 3, headerPaint)

            for (c in 0 until numCols) {
                val left = rw + c * cs
                val top = ch + r * cs

                if (gridState[r][c]) {
                    val right = left + cs
                    val bottom = top + cs
                    canvas.drawLine(left, top, right, bottom, xPaint)
                    canvas.drawLine(left, bottom, right, top, xPaint)
                }
            }
        }
    }

    private fun refreshHeaderDimensions() {
        updateHeaderDimensions()
        requestLayout()
        invalidate()
    }

    // ==================== The rest of your methods (touch, drag, menu, etc.) remain the same ====================
    // (I kept them exactly as in your last file for consistency)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val ch = colHeaderHeightDp * d
        val rw = rowHeaderWidthDp * d

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = touchX
                startY = touchY
                downTime = System.currentTimeMillis()

                pressedRow = -1
                pressedCol = -1
                isDragging = false
                downHeaderType = 0

                if (touchX > rw && touchY > ch) {
                    downCol = ((touchX - rw) / cs).toInt().coerceIn(0, numCols - 1)
                    downRow = ((touchY - ch) / cs).toInt().coerceIn(0, numRows - 1)
                    pressedRow = downRow
                    pressedCol = downCol
                } else if (touchY < ch && touchX > rw) {
                    downCol = ((touchX - rw) / cs).toInt().coerceIn(0, numCols - 1)
                    downHeaderType = 2
                } else if (touchX < rw && touchY > ch) {
                    downRow = ((touchY - ch) / cs).toInt().coerceIn(0, numRows - 1)
                    downHeaderType = 1
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(touchX - startX)
                val dy = abs(touchY - startY)

                if (isDragging) {
                    handleDrag(touchX, touchY, ch, cs)
                    return true
                }

                if (downHeaderType != 0 && (dx > touchSlop || dy > touchSlop)) {
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
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - downTime
                val moved = abs(touchX - startX) > touchSlop || abs(touchY - startY) > touchSlop

                if (!isDragging) {
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
                cleanupTouch()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cleanupTouch()
                return true
            }
        }
        return false
    }

    private fun handleDrag(touchX: Float, touchY: Float, ch: Float, cs: Float) {
        val rw = rowHeaderWidthDp * resources.displayMetrics.density

        val target = if (dragType == 1) {
            ((touchY - ch + cs / 2) / cs).toInt().coerceIn(0, numRows - 1)
        } else {
            ((touchX - rw + cs / 2) / cs).toInt().coerceIn(0, numCols - 1)
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
            setBackgroundColor(Color.parseColor("#333333"))
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
        rowHeaders.add(position, "Row ${position + 1}")
        if (selectedRow >= position) selectedRow++
        refreshHeaderDimensions()
        requestLayout()
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
        requestLayout()
        invalidate()
    }

    private fun insertColumnAt(position: Int) {
        numCols++
        gridState.forEach { it.add(position, false) }
        colHeaders.add(position, "Col ${position + 1}")
        if (selectedCol >= position) selectedCol++
        refreshHeaderDimensions()
        requestLayout()
        invalidate()
    }

    private fun deleteColumnAt(position: Int) {
        if (numCols <= 1) return
        numCols--
        gridState.forEach { it.removeAt(position) }
        colHeaders.removeAt(position)
        if (selectedCol == position) selectedCol = -1
        else if (selectedCol > position) selectedCol--
        refreshHeaderDimensions()
        requestLayout()
        invalidate()
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

        refreshHeaderDimensions()
        requestLayout()
        invalidate()
    }
}