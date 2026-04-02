package com.example.bulletjournalgrid   // your package

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gridView = findViewById<BulletJournalGridView>(R.id.gridView)

        findViewById<android.widget.Button>(R.id.btnAddRow).setOnClickListener { gridView.addRow() }
        findViewById<android.widget.Button>(R.id.btnAddColumn).setOnClickListener { gridView.addColumn() }
    }
}