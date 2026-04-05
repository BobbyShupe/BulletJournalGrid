package com.example.bulletjournalgrid

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

        // Create a default grid if none exists
        if (savedGrids.isEmpty()) {
            val defaultGrid = GridData(
                name = "My First Grid",
                numRows = 5,
                numCols = 5
            )
            savedGrids.add(defaultGrid)
            currentGridIndex = 0
            gridView.loadGridData(defaultGrid)
        } else {
            loadGrid(0)
        }

        setupSpinner()

        // Button listeners
        findViewById<android.widget.Button>(R.id.btnAddRow).setOnClickListener {
            gridView.addRow()
        }

        findViewById<android.widget.Button>(R.id.btnAddColumn).setOnClickListener {
            gridView.addColumn()
        }

        findViewById<android.widget.Button>(R.id.btnSaveGrid).setOnClickListener {
            val newName = "Grid ${savedGrids.size + 1}"
            saveCurrentGrid(newName)
        }

        // Spinner selection
        gridSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position != currentGridIndex && position < savedGrids.size) {
                    loadGrid(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun loadSavedGrids() {
        val json = prefs.getString(gridsKey, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<GridData>>() {}.type
                val loadedGrids: List<GridData> = gson.fromJson(json, type)
                savedGrids.clear()
                savedGrids.addAll(loadedGrids)
            } catch (e: Exception) {
                // Fallback if corrupted
                savedGrids.clear()
            }
        }
    }

    private fun saveGridsToPrefs() {
        val json = gson.toJson(savedGrids)
        prefs.edit().putString(gridsKey, json).apply()
    }

    private fun saveCurrentGrid(name: String) {
        val currentData = gridView.getCurrentGridData().copy(name = name)
        savedGrids.add(currentData)
        currentGridIndex = savedGrids.size - 1
        saveGridsToPrefs()
        setupSpinner()
        gridSpinner.setSelection(currentGridIndex)
    }

    private fun setupSpinner() {
        val names = savedGrids.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSpinner.adapter = adapter
    }

    private fun loadGrid(index: Int) {
        if (index < 0 || index >= savedGrids.size) return
        currentGridIndex = index
        gridView.loadGridData(savedGrids[index])
        gridSpinner.setSelection(index)
    }

    // Auto-save changes when app goes to background
    override fun onPause() {
        super.onPause()
        if (currentGridIndex >= 0 && currentGridIndex < savedGrids.size) {
            savedGrids[currentGridIndex] = gridView.getCurrentGridData().copy(
                name = savedGrids[currentGridIndex].name
            )
            saveGridsToPrefs()
        }
    }
}