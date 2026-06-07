package com.example.gamehub.utils

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthHelper(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("GameHub", Context.MODE_PRIVATE)

    fun isAuthenticated(): Boolean {
        return auth.currentUser != null
    }

    suspend fun signInAnonymously(): String {
        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: throw Exception("Sign in failed")
        prefs.edit().putString("user_uid", uid).apply()
        return uid
    }

    fun getCurrentUid(): String {
        return auth.currentUser?.uid ?: prefs.getString("user_uid", "") ?: ""
    }

    suspend fun createUser(uid: String, playerName: String) {
        val userMap = hashMapOf(
            "uid" to uid,
            "playerName" to playerName,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid).set(userMap).await()
        prefs.edit().putString("player_name", playerName).apply()
    }
    suspend fun updatePlayerName(uid: String, newName: String) {

        db.collection("users")
            .document(uid)
            .update("playerName", newName)
            .await()

        prefs.edit()
            .putString("player_name", newName)
            .apply()
    }
    suspend fun getPlayerName(uid: String): String {
        val doc = db.collection("users").document(uid).get().await()
        return doc.getString("playerName") ?: ""
    }
}