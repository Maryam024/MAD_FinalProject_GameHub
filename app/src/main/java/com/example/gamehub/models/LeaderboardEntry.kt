
// models/LeaderboardEntry.kt
package com.example.gamehub.models

import com.google.firebase.Timestamp

data class LeaderboardEntry(
    val uid: String = "",
    val playerName: String = "",
    val gameName: String = "",
    val score: Int = 0,
    val timestamp: Timestamp = Timestamp.now(),
    val rank: Int = 0
)