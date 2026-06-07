// models/UserData.kt
package com.example.gamehub.models

data class UserData(
    val userId: String = "",
    val playerName: String = "Player",
    val avatarRes: Int = 0,
    val totalScore: Int = 0,
    val gamesPlayed: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)



