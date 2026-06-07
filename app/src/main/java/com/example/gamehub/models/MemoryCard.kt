package com.example.gamehub.models

data class MemoryCard(
    var id: Int,
    val cardId: Int, // For matching pairs
    val imageRes: Int,
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false,
    val cardValue: String = "" // For emoji cards
)