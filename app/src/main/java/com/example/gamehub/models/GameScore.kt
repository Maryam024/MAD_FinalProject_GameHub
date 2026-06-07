// models/GameScore.kt
package com.example.gamehub.models

import com.google.firebase.Timestamp

data class GameScore(
    val id: String = "",
    val uid: String = "",
    val playerName: String = "",
    val gameName: String = "",
    val score: Int = 0,
    val timestamp: Timestamp = Timestamp.now(),
    val moves: Int = 0,
    val timeSeconds: Int = 0,
    val powerUpsUsed: Int = 0,
    val won: Boolean = false,
    val metadata: Map<String, Any>? = null
) {
    constructor() : this("", "", "", "")
}
