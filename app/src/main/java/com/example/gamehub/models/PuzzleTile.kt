package com.example.gamehub.models

data class PuzzleTile(
    val id: Int,
    val originalPosition: Int,
    var currentPosition: Int,
    var bitmap: android.graphics.Bitmap? = null,
    var isEmpty: Boolean = false
)