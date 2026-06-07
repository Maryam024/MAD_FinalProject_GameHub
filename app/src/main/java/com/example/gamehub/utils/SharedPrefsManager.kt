// utils/SharedPrefsManager.kt
package com.example.gamehub.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages local storage of user preferences and cached data
 * Tracks user state across app restarts
 */
class SharedPrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("GameHubPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYER_NAME = "player_name"
        private const val KEY_SAVED_UID = "saved_uid"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PROFILE_CREATED = "profile_created"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"

        // Game high scores cache
        private const val KEY_GAME_SCORE_PREFIX = "game_score_"
    }

    // ==================== USER DATA ====================

    fun savePlayerName(name: String) {
        prefs.edit().putString(KEY_PLAYER_NAME, name).apply()
    }

    fun getPlayerName(): String {
        return prefs.getString(KEY_PLAYER_NAME, "") ?: ""
    }

    fun saveUid(uid: String) {
        prefs.edit().putString(KEY_SAVED_UID, uid).apply()
    }

    fun getSavedUid(): String {
        return prefs.getString(KEY_SAVED_UID, "") ?: ""
    }

    fun hasUserData(): Boolean {
        return getSavedUid().isNotEmpty()
    }

    fun clearUserData() {
        prefs.edit()
            .remove(KEY_SAVED_UID)
            .remove(KEY_PLAYER_NAME)
            .apply()
    }

    // ==================== APP STATE ====================

    fun isFirstLaunch(): Boolean {
        val isFirst = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (isFirst) {
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        }
        return isFirst
    }

    fun setProfileCreated(created: Boolean) {
        prefs.edit().putBoolean(KEY_PROFILE_CREATED, created).apply()
    }

    fun isProfileCreated(): Boolean {
        return prefs.getBoolean(KEY_PROFILE_CREATED, false)
    }

    // ==================== CACHED SCORES ====================

    fun cacheGameScore(gameId: String, score: Int) {
        prefs.edit().putInt("$KEY_GAME_SCORE_PREFIX$gameId", score).apply()
    }

    fun getCachedGameScore(gameId: String): Int {
        return prefs.getInt("$KEY_GAME_SCORE_PREFIX$gameId", 0)
    }

    fun updateLastSyncTime() {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis()).apply()
    }

    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }

    // ==================== CLEANUP ====================

    fun clearAll() {
        prefs.edit().clear().apply()
    }
    // Add this method
    fun updateScore(gameId: String, score: Int) {
        prefs.edit().putInt("score_$gameId", score).apply()
        val currentTotal = getTotalScore()
        prefs.edit().putInt("total_score", currentTotal + score).apply()
    }

    fun getGameScore(gameId: String): Int {
        return prefs.getInt("score_$gameId", 0)
    }

    fun getTotalScore(): Int {
        return prefs.getInt("total_score", 0)
    }
}