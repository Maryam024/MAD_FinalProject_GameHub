// utils/FirebaseManager.kt
package com.example.gamehub.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseManager"
        @Volatile
        private var INSTANCE: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = SharedPrefsManager(context)

    // Get unique user ID
    fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: "unknown_user"
    }

    // Check if user is logged in
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    // Initialize Firebase for this user (call once when app starts)
    fun initializeUser(onComplete: (Boolean, String) -> Unit) {
        if (auth.currentUser != null) {
            // Already signed in, just update profile
            updateUserProfile()
            onComplete(true, auth.currentUser?.uid ?: "")
            return
        }

        // Sign in anonymously (creates unique ID for this device/user)
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val userId = result.user?.uid ?: ""
                Log.d(TAG, "User initialized: $userId")

                // Create or update user profile in Firestore
                createUserProfile(userId)
                onComplete(true, userId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Auth failed: ${e.message}")
                onComplete(false, e.message ?: "Unknown error")
            }
    }

    private fun createUserProfile(userId: String) {
        val userData: MutableMap<String, Any> = HashMap()
        userData["userId"] = userId
        userData["playerName"] = prefs.getPlayerName()
        userData["createdAt"] = System.currentTimeMillis()
        userData["lastActive"] = System.currentTimeMillis()

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "User profile created for $userId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create profile: ${e.message}")
            }
    }

    private fun updateUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        val updates: MutableMap<String, Any> = HashMap()
        updates["playerName"] = prefs.getPlayerName()
        updates["totalScore"] = prefs.getTotalScore()
        updates["lastActive"] = System.currentTimeMillis()

        db.collection("users").document(userId)
            .update(updates)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update profile: ${e.message}")
            }
    }

    // Save score with user distinction
    fun saveGameScore(gameName: String, score: Int, moves: Int = 0, timeSeconds: Int = 0) {
        val userId = auth.currentUser?.uid ?: return
        val playerName = prefs.getPlayerName()

        val scoreData: MutableMap<String, Any> = HashMap()
        scoreData["userId"] = userId
        scoreData["playerName"] = playerName
        scoreData["gameName"] = gameName
        scoreData["score"] = score
        scoreData["moves"] = moves
        scoreData["timeSeconds"] = timeSeconds
        scoreData["timestamp"] = System.currentTimeMillis()
        scoreData["deviceInfo"] = android.os.Build.MODEL

        // Save individual game score
        db.collection("scores").add(scoreData)
            .addOnSuccessListener {
                Log.d(TAG, "Score saved for $playerName ($userId): $score in $gameName")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save score: ${e.message}")
            }
    }

    // Get global leaderboard for a specific game
    fun getGameLeaderboard(gameName: String, limit: Int = 10, callback: (List<LeaderboardEntry>) -> Unit) {
        db.collection("scores")
            .whereEqualTo("gameName", gameName)
            .orderBy("score", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .addOnSuccessListener { result ->
                val entries = mutableListOf<LeaderboardEntry>()
                var index = 1
                for (document in result.documents) {
                    val playerName = document.getString("playerName") ?: "Anonymous"
                    val score = document.getLong("score")?.toInt() ?: 0
                    entries.add(LeaderboardEntry(index, playerName, score))
                    index++
                }
                callback(entries)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get leaderboard: ${e.message}")
                callback(emptyList())
            }
    }
}