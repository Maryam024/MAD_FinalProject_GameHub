package com.example.gamehub.models

data class Question(
    val id: Int,
    val text: String,
    val options: List<String>,
    val correctAnswer: Int,
    val category: String,
    val difficulty: String = "Medium",
    val points: Int = 100
)