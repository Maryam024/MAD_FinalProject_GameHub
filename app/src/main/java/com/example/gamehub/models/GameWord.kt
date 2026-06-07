package com.example.gamehub.models

data class GameWord(
    val word: String,
    val category: WordCategory,
    val difficulty: String,
    val hint: String,
    val points: Int = 100
)