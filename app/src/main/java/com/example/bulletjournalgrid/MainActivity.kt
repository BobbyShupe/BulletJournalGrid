package com.example.bulletjournalgrid

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.EditText
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    private lateinit var gridView: BulletJournalGridView
    private lateinit var gridSpinner: android.widget.Spinner

    private val savedGrids = mutableListOf<GridData>()
    private var currentGridIndex = -1

    private val gson = Gson()
    private val prefs by lazy {
        getSharedPreferences("bullet_journal_prefs", Context.MODE_PRIVATE)
    }
    private val gridsKey = "saved_grids"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView = findViewById(R.id.gridView)
        gridSpinner = findViewById(R.id.gridSpinner)

        loadSavedGrids()

        if (savedGrids.isEmpty()) {
            val defaultGrid = GridData(name = "My First Grid")
            savedGrids.add(defaultGrid)
            currentGridIndex = 0
            gridView.loadGridData(defaultGrid)
        } else {
            loadGrid(0)
        }

        setupSpinner()

        // Button listeners
        findViewById<android.widget.Button>(R.id.btnAddRow).setOnClickListener { gridView.addRow() }
        findViewById<android.widget.Button>(R.id.btnAddColumn).setOnClickListener { gridView.addColumn() }

        // Changed: "New Grid" button now creates a fresh grid
        findViewById<android.widget.Button>(R.id.btnNewGrid).setOnClickListener {
            createNewGrid()
        }

        // Short tap: switch grid
        gridSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position != currentGridIndex && position < savedGrids.size) {
                    loadGrid(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Long press on spinner → menu
        gridSpinner.setOnLongClickListener {
            val selectedPosition = gridSpinner.selectedItemPosition
            if (selectedPosition >= 0 && selectedPosition < savedGrids.size) {
                showGridOptionsMenu(selectedPosition, it)
            }
            true
        }
    }

    private fun setupSpinner() {
        val names = savedGrids.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSpinner.adapter = adapter
    }

    private fun loadSavedGrids() {
        val json = prefs.getString(gridsKey, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<GridData>>() {}.type
                val loaded: List<GridData> = gson.fromJson(json, type)
                savedGrids.clear()
                savedGrids.addAll(loaded)
            } catch (e: Exception) {
                savedGrids.clear()
            }
        }
    }

    private fun saveGridsToPrefs() {
        val json = gson.toJson(savedGrids)
        prefs.edit().putString(gridsKey, json).apply()
    }

    private fun loadGrid(index: Int) {
        if (index < 0 || index >= savedGrids.size) return

        // === CRITICAL FIX: Save current grid before switching ===
        saveCurrentGridIfNeeded()

        // Now load the new grid
        currentGridIndex = index
        gridView.loadGridData(savedGrids[index])
        gridSpinner.setSelection(index)
    }

    // ==================== NEW GRID (used by both button and menu) ====================
    private fun createNewGrid() {
        saveCurrentGridIfNeeded()
        val newName = "Grid ${savedGrids.size + 1}"
        val newGrid = GridData(name = newName)

        savedGrids.add(newGrid)
        currentGridIndex = savedGrids.size - 1

        gridView.loadGridData(newGrid)
        saveGridsToPrefs()
        setupSpinner()
        gridSpinner.setSelection(currentGridIndex)
    }

    // ==================== LONG-PRESS MENU ====================
    private fun showGridOptionsMenu(position: Int, anchorView: View) {
        val popup = PopupMenu(this, anchorView)

        popup.menu.add("New Grid")
        popup.menu.add("Rename")
        popup.menu.add("Delete")

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.title.toString()) {
                "New Grid" -> createNewGrid()
                "Rename"   -> showRenameGridDialog(position)
                "Delete"   -> showDeleteConfirmation(position)
            }
            true
        }
        popup.show()
    }

    private fun showRenameGridDialog(position: Int) {
        val currentName = savedGrids[position].name
        val input = EditText(this).apply {
            setText(currentName)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Grid")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    savedGrids[position] = savedGrids[position].copy(name = newName)
                    saveGridsToPrefs()
                    setupSpinner()
                    gridSpinner.setSelection(position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(position: Int) {
        if (savedGrids.size <= 1) {
            AlertDialog.Builder(this)
                .setTitle("Cannot Delete")
                .setMessage("You must keep at least one grid.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Grid")
            .setMessage("Delete \"${savedGrids[position].name}\" permanently?")
            .setPositiveButton("Delete") { _, _ ->
                savedGrids.removeAt(position)
                saveGridsToPrefs()

                if (currentGridIndex >= position) {
                    currentGridIndex = (currentGridIndex - 1).coerceAtLeast(0)
                }
                loadGrid(currentGridIndex)
                setupSpinner()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Auto-save current changes when app pauses
    override fun onPause() {
        super.onPause()
        if (currentGridIndex >= 0 && currentGridIndex < savedGrids.size) {
            savedGrids[currentGridIndex] = gridView.getCurrentGridData().copy(
                name = savedGrids[currentGridIndex].name
            )
            saveGridsToPrefs()
        }
        saveCurrentGridIfNeeded()
    }

    private fun saveCurrentGridIfNeeded() {
        if (currentGridIndex >= 0 && currentGridIndex < savedGrids.size) {
            val currentData = gridView.getCurrentGridData()

            // Preserve the original name (don't overwrite it with "Unnamed")
            savedGrids[currentGridIndex] = currentData.copy(
                name = savedGrids[currentGridIndex].name
            )
        }
    }
}