package com.example.bulletjournalgrid

data class GridData(
    val name: String = "Untitled Grid",
    val numRows: Int = 5,
    val numCols: Int = 5,
    val gridState: List<List<Boolean>> = emptyList(),
    val colHeaders: List<String> = emptyList(),
    val rowHeaders: List<String> = emptyList(),
    val isDateMode: Boolean = false
)