package com.example.gamehub.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ScoreHelper {

    private val db = FirebaseFirestore.getInstance()

    suspend fun saveScore(
        uid: String,
        playerName: String,
        gameName: String,
        score: Int
    ) {

        val scoreData = hashMapOf(
            "uid" to uid,
            "playerName" to playerName,
            "gameName" to gameName,
            "score" to score,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("scores")
            .add(scoreData)
            .await()
    }
}