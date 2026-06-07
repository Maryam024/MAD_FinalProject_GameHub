package com.example.gamehub.utils

import com.example.gamehub.models.GameScore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class LeaderboardRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getHighScore(
        uid: String,
        gameName: String
    ): Int {

        val snapshot =
            db.collection("scores")
                .whereEqualTo("uid", uid)
                .whereEqualTo("gameName", gameName)
                .orderBy("score", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

        if (snapshot.isEmpty) return 0

        return snapshot.documents[0]
            .getLong("score")
            ?.toInt() ?: 0
    }

    suspend fun getLastScore(
        uid: String,
        gameName: String
    ): Int {

        val snapshot =
            db.collection("scores")
                .whereEqualTo("uid", uid)
                .whereEqualTo("gameName", gameName)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

        if (snapshot.isEmpty) return 0

        return snapshot.documents[0]
            .getLong("score")
            ?.toInt() ?: 0
    }
}