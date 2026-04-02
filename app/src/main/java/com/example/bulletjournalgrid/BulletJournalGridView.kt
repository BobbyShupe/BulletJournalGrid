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

    private val pressPaint = Paint().apply {
        color = Color.parseColor("#80FFFFFF") // brighter for press feedback
        style = Paint.Style.FILL
    }

    // Drag state
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var dragType = 0 // 0=none, 1=row, 2=column
    private var draggedIndex = -1
    private var highlightIndex = -1

    // Tap state
    private var downRow = -1
    private var downCol = -1
    private var isPotentialTap = false

    // Press highlight
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

        // Drag highlight (row/column)
        if (highlightIndex >= 0) {
            if (dragType == 1) {
                val top = hs + highlightIndex * cs
                canvas.drawRect(0f, top, width.toFloat(), top + cs, highlightPaint)
            } else if (dragType == 2) {
                val left = hs + highlightIndex * cs
                canvas.drawRect(left, 0f, left + cs, height.toFloat(), highlightPaint)
            }
        }

        // Press highlight (cell)
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

        // Rows + cells
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

    // Add this to your class properties
    private var downTime = 0L

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
                isPotentialTap = true
                isDragging = false
                dragType = 0

                // Grid Cell Detection
                if (touchX > hs && touchY > hs) {
                    downCol = ((touchX - hs) / cs).toInt()
                    downRow = ((touchY - hs) / cs).toInt()
                    pressedRow = downRow
                    pressedCol = downCol
                } else {
                    // Header Detection
                    if (touchY < hs && touchX > hs) {
                        draggedIndex = ((touchX - hs) / cs).toInt()
                        dragType = 2
                    } else if (touchX < hs && touchY > hs) {
                        draggedIndex = ((touchY - hs) / cs).toInt()
                        dragType = 1
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(touchX - startX)
                val dy = abs(touchY - startY)

                if (dx > touchSlop || dy > touchSlop) {
                    // Immediately kill the tap potential
                    isPotentialTap = false
                    pressedRow = -1
                    pressedCol = -1

                    if (dragType == 0) {
                        // Hand off to ScrollView
                        parent.requestDisallowInterceptTouchEvent(false)
                        invalidate()
                        return false
                    } else {
                        isDragging = true
                        parent.requestDisallowInterceptTouchEvent(true)
                    }
                }

                // Standard Drag/Swap Logic
                if (isDragging) {
                    val target = if (dragType == 1) {
                        ((touchY - hs + cs / 2) / cs).toInt().coerceIn(0, numRows - 1)
                    } else {
                        ((touchX - hs + cs / 2) / cs).toInt().coerceIn(0, numCols - 1)
                    }

                    if (target != draggedIndex) {
                        if (dragType == 1) swapRows(draggedIndex, target)
                        else swapColumns(draggedIndex, target)
                        draggedIndex = target
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - downTime

                // A tap should be quick and stay within the slop
                // 200ms is a standard threshold for a "tap"
                if (isPotentialTap && dragType == 0 && duration < 200) {
                    if (downRow in 0 until numRows && downCol in 0 until numCols) {
                        gridState[downRow][downCol] = !gridState[downRow][downCol]
                    }
                }
                cleanupTouch()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // If the ScrollView takes over, we land here.
                // Do NOTHING but reset the UI.
                cleanupTouch()
                return true
            }
        }
        return false
    }

    private fun cleanupTouch() {
        isPotentialTap = false
        isDragging = false
        dragType = 0
        pressedRow = -1
        pressedCol = -1
        downRow = -1
        downCol = -1
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
                if (isColumn) colHeaders[index] = newName else rowHeaders[index] = newName
                invalidate()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    fun addRow() = insertRowAt(numRows)
    fun addColumn() = insertColumnAt(numCols)

    private fun insertRowAt(position: Int) {
        numRows++
        gridState.add(position, MutableList(numCols) { false })
        rowHeaders.add(position, "Row ${position + 1}")
        requestLayout()
        invalidate()
    }

    private fun deleteRowAt(position: Int) {
        if (numRows <= 1) return
        numRows--
        gridState.removeAt(position)
        rowHeaders.removeAt(position)
        requestLayout()
        invalidate()
    }

    private fun insertColumnAt(position: Int) {
        numCols++
        gridState.forEach { it.add(position, false) }
        colHeaders.add(position, "Col ${position + 1}")
        requestLayout()
        invalidate()
    }

    private fun deleteColumnAt(position: Int) {
        if (numCols <= 1) return
        numCols--
        gridState.forEach { it.removeAt(position) }
        colHeaders.removeAt(position)
        requestLayout()
        invalidate()
    }
}