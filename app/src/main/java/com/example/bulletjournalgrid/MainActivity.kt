package com.example.bulletjournalgrid

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var gridView: BulletJournalGridView
    private lateinit var gridSpinner: android.widget.Spinner
    private val savedGrids = mutableListOf<GridData>()
    private var currentGridIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView = findViewById(R.id.gridView)
        gridSpinner = findViewById(R.id.gridSpinner)

        // Initial empty grid
        saveCurrentGrid("Grid 1")

        setupSpinner()

        findViewById<android.widget.Button>(R.id.btnAddRow).setOnClickListener {
            gridView.addRow()
        }

        findViewById<android.widget.Button>(R.id.btnAddColumn).setOnClickListener {
            gridView.addColumn()
        }

        findViewById<android.widget.Button>(R.id.btnSaveGrid).setOnClickListener {
            showSaveDialog()
        }

        // Spinner selection listener
        gridSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (position != currentGridIndex) {
                    loadGrid(position)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, savedGrids.map { it.name })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        gridSpinner.adapter = adapter
    }

    private fun saveCurrentGrid(name: String) {
        val gridData = gridView.getCurrentGridData().copy(name = name)
        savedGrids.add(gridData)
        currentGridIndex = savedGrids.size - 1
        setupSpinner()
        gridSpinner.setSelection(currentGridIndex)
    }

    private fun showSaveDialog() {
        // For simplicity, we'll auto-name it. You can replace this with a proper dialog later.
        val newName = "Grid ${savedGrids.size + 1}"
        saveCurrentGrid(newName)
    }

    private fun loadGrid(index: Int) {
        if (index < 0 || index >= savedGrids.size) return

        currentGridIndex = index
        val data = savedGrids[index]
        gridView.loadGridData(data)
    }
}