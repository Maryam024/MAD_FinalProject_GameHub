// models/User.kt
package com.example.gamehub.models

import com.google.firebase.Timestamp

data class User(
    val uid: String = "",
    val playerName: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val lastLogin: Timestamp = Timestamp.now(),
    val totalScore: Int = 0,
    val gamesPlayed: Int = 0,
    val highestScore: Int = 0,
    val deviceInfo: Map<String, String>? = null
) {
    // Firestore requires empty constructor for deserialization
    constructor() : this("", "", Timestamp.now(), Timestamp.now())
}



