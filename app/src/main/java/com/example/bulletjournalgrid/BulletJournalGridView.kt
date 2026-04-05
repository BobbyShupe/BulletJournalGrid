package com.example.bulletjournalgrid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs

class BulletJournalGridView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var numRows = 5
    private var numCols = 5

    private val cellSizeDp = 25f
    private val headerSizeDp = 48f

    private val gridState = mutableListOf<MutableList<Boolean>>()
    private val colHeaders = mutableListOf<String>()
    private val rowHeaders = mutableListOf<String>()

    // Selection
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
        textSize = 17f
        textAlign = Paint.Align.CENTER
    }

    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#40FFFFFF")
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF")  // Stronger highlight for selected row/column
        style = Paint.Style.FILL
    }

    private val pressPaint = Paint().apply {
        color = Color.parseColor("#60FFFFFF")
        style = Paint.Style.FILL
    }

    // Touch handling
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var downRow = -1
    private var downCol = -1
    private var downHeaderType = 0 // 0=none, 1=row header, 2=col header

    // Drag state
    private var isDragging = false
    private var dragType = 0 // 1=row, 2=column
    private var draggedIndex = -1

    // Press feedback
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
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val w = (headerSizeDp + numCols * cellSizeDp) * d
        val h = (headerSizeDp + numRows * cellSizeDp) * d
        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val hs = headerSizeDp * d

        // Selection highlight (full row or column)
        if (selectedRow >= 0) {
            val top = hs + selectedRow * cs
            canvas.drawRect(0f, top, width.toFloat(), top + cs, selectionPaint)
        }
        if (selectedCol >= 0) {
            val left = hs + selectedCol * cs
            canvas.drawRect(left, 0f, left + cs, height.toFloat(), selectionPaint)
        }

        // Press highlight (temporary cell press)
        if (pressedRow >= 0 && pressedCol >= 0) {
            val left = hs + pressedCol * cs
            val top = hs + pressedRow * cs
            canvas.drawRect(left, top, left + cs, top + cs, pressPaint)
        }

        // Column headers
        for (c in 0 until numCols) {
            val cx = hs + c * cs + cs / 2
            val cy = hs / 2f
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            canvas.drawText(colHeaders[c], cx, cy + headerPaint.textSize / 3, headerPaint)
            canvas.restore()
        }

        // Row headers + cells
        for (r in 0 until numRows) {
            val y = hs + r * cs + cs / 2
            canvas.drawText(rowHeaders[r], hs / 2, y + headerPaint.textSize / 3, headerPaint)

            for (c in 0 until numCols) {
                val left = hs + c * cs
                val top = hs + r * cs
                val right = left + cs
                val bottom = top + cs

                canvas.drawRect(left, top, right, bottom, borderPaint)

                if (gridState[r][c]) {
                    canvas.drawLine(left, top, right, bottom, xPaint)
                    canvas.drawLine(left, bottom, right, top, xPaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val d = resources.displayMetrics.density
        val cs = cellSizeDp * d
        val hs = headerSizeDp * d

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

                // Determine what was touched
                if (touchX > hs && touchY > hs) {
                    // Grid cell
                    downCol = ((touchX - hs) / cs).toInt().coerceIn(0, numCols - 1)
                    downRow = ((touchY - hs) / cs).toInt().coerceIn(0, numRows - 1)
                    pressedRow = downRow
                    pressedCol = downCol
                } else if (touchY < hs && touchX > hs) {
                    // Column header
                    downCol = ((touchX - hs) / cs).toInt().coerceIn(0, numCols - 1)
                    downHeaderType = 2
                } else if (touchX < hs && touchY > hs) {
                    // Row header
                    downRow = ((touchY - hs) / cs).toInt().coerceIn(0, numRows - 1)
                    downHeaderType = 1
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(touchX - startX)
                val dy = abs(touchY - startY)

                if (isDragging) {
                    handleDrag(touchX, touchY, hs, cs)
                    return true
                }

                // Start dragging only after long press + movement on a selected header
                if (downHeaderType != 0 && (dx > touchSlop || dy > touchSlop)) {
                    val isSelected = if (downHeaderType == 1) downRow == selectedRow else downCol == selectedCol

                    if (isSelected) {
                        isDragging = true
                        dragType = downHeaderType
                        draggedIndex = if (dragType == 1) downRow else downCol
                        parent.requestDisallowInterceptTouchEvent(true)
                        handleDrag(touchX, touchY, hs, cs)
                    } else {
                        // Moved too much without being selected → cancel
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
                        // Tap on header
                        downHeaderType == 1 && !moved -> {
                            if (downRow == selectedRow) {
                                selectedRow = -1  // deselect if tapping same row
                            } else {
                                selectedRow = downRow
                                selectedCol = -1
                            }
                        }
                        downHeaderType == 2 && !moved -> {
                            if (downCol == selectedCol) {
                                selectedCol = -1
                            } else {
                                selectedCol = downCol
                                selectedRow = -1
                            }
                        }
                        // Long press on header (no significant movement)
                        downHeaderType != 0 && duration >= longPressTimeout -> {
                            val isSelected = if (downHeaderType == 1) downRow == selectedRow else downCol == selectedCol
                            if (isSelected) {
                                // Long press on selected → should have started drag already, but fallback
                            } else {
                                showHeaderMenu(downHeaderType == 2, if (downHeaderType == 1) downRow else downCol)
                            }
                        }
                        // Tap on cell
                        downRow >= 0 && downCol >= 0 && !moved -> {
                            gridState[downRow][downCol] = !gridState[downRow][downCol]
                            selectedRow = -1
                            selectedCol = -1
                        }
                        // Tap elsewhere → clear selection
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

    private fun handleDrag(touchX: Float, touchY: Float, hs: Float, cs: Float) {
        val target = if (dragType == 1) {
            ((touchY - hs + cs / 2) / cs).toInt().coerceIn(0, numRows - 1)
        } else {
            ((touchX - hs + cs / 2) / cs).toInt().coerceIn(0, numCols - 1)
        }

        if (target != draggedIndex) {
            if (dragType == 1) swapRows(draggedIndex, target)
            else swapColumns(draggedIndex, target)
            draggedIndex = target

            // Keep selection on the moved item
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
    }

    private fun swapColumns(from: Int, to: Int) {
        if (from == to) return
        for (row in gridState) {
            row.add(to, row.removeAt(from))
        }
        colHeaders.add(to, colHeaders.removeAt(from))
    }

    private fun showHeaderMenu(isColumn: Boolean, index: Int) {
        val items = arrayOf("Rename", "Delete")

        AlertDialog.Builder(context)
            .setTitle(if (isColumn) "Column ${index + 1}" else "Row ${index + 1}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameDialog(isColumn, index)
                    1 -> if (isColumn) deleteColumnAt(index) else deleteRowAt(index)
                }
            }
            .show()
    }

    private fun showRenameDialog(isColumn: Boolean, index: Int) {
        val current = if (isColumn) colHeaders[index] else rowHeaders[index]
        val input = EditText(context).apply {
            setText(current)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.DKGRAY)
        }

        AlertDialog.Builder(context)
            .setTitle(if (isColumn) "Rename Column" else "Rename Row")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (isColumn) colHeaders[index] = newName else rowHeaders[index] = newName
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
        // Adjust selection if needed
        if (selectedRow >= position) selectedRow++
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
        requestLayout()
        invalidate()
    }

    private fun insertColumnAt(position: Int) {
        numCols++
        gridState.forEach { it.add(position, false) }
        colHeaders.add(position, "Col ${position + 1}")
        if (selectedCol >= position) selectedCol++
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
        requestLayout()
        invalidate()
    }
    // === NEW: Save / Load functionality ===

    fun getCurrentGridData(): GridData {
        return GridData(
            name = "Unnamed Grid", // Will be overridden in MainActivity
            numRows = numRows,
            numCols = numCols,
            gridState = gridState.map { it.toList() },           // Deep copy
            colHeaders = colHeaders.toList(),
            rowHeaders = rowHeaders.toList()
        )
    }

    fun loadGridData(data: GridData) {
        numRows = data.numRows
        numCols = data.numCols

        gridState.clear()
        data.gridState.forEach { row ->
            gridState.add(row.toMutableList())
        }

        colHeaders.clear()
        colHeaders.addAll(data.colHeaders)

        rowHeaders.clear()
        rowHeaders.addAll(data.rowHeaders)

        selectedRow = -1
        selectedCol = -1

        requestLayout()
        invalidate()
    }
}