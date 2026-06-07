package com.example.gamehub.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

object GameStatsHelper {

    private val db = FirebaseFirestore.getInstance()

    suspend fun getLastScore(uid: String, gameName: String): Int {
        return try {
            val snapshot = db.collection("scores")
                .get()
                .await()

            snapshot.documents
                .filter {
                    it.getString("uid") == uid &&
                            it.getString("gameName") == gameName
                }
                .maxByOrNull { it.getLong("timestamp") ?: 0 }
                ?.getLong("score")
                ?.toInt() ?: 0

        } catch (e: Exception) {
            android.util.Log.e("GAME_STATS", "Last Score Error", e)
            0
        }
    }

    suspend fun getBestScore(uid: String, gameName: String): Int {
        return try {
            val snapshot = db.collection("scores")
                .get()
                .await()

            snapshot.documents
                .filter {
                    it.getString("uid") == uid &&
                            it.getString("gameName") == gameName
                }
                .maxByOrNull { it.getLong("score") ?: 0 }
                ?.getLong("score")
                ?.toInt() ?: 0

        } catch (e: Exception) {
            android.util.Log.e("GAME_STATS", "Best Score Error", e)
            0
        }
    }

    suspend fun getGlobalLeaderboard(gameName: String, limit: Int = 20): List<LeaderboardEntry> {
        return try {
            val snapshot = db.collection("scores")
                .get()
                .await()

            snapshot.documents
                .filter { it.getString("gameName") == gameName }
                .sortedByDescending { it.getLong("score") ?: 0 }
                .take(limit)
                .mapIndexed { index, doc ->
                    LeaderboardEntry(
                        rank = index + 1,
                        playerName = doc.getString("playerName") ?: "Anonymous",
                        score = doc.getLong("score")?.toInt() ?: 0
                    )
                }
        } catch (e: Exception) {
            android.util.Log.e("GAME_STATS", "Leaderboard Error", e)
            emptyList()
        }
    }
}

data class LeaderboardEntry(val rank: Int, val playerName: String, val score: Int)