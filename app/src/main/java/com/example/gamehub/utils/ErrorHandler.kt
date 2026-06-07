// utils/ErrorHandler.kt
package com.example.gamehub.utils

import android.content.Context
import android.widget.Toast
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Centralized error handling for Firebase operations
 */
object ErrorHandler {

    fun handleFirebaseError(
        context: Context,
        exception: Exception,
        fallbackMessage: String = "An error occurred"
    ): String {
        val message = when (exception) {
            is FirebaseNetworkException -> "No internet connection. Please check your network."
            is FirebaseAuthException -> {
                when (exception.errorCode) {
                    "ERROR_INVALID_CUSTOM_TOKEN" -> "Invalid authentication token."
                    "ERROR_CUSTOM_TOKEN_MISMATCH" -> "Authentication error."
                    "ERROR_INVALID_CREDENTIAL" -> "Invalid credentials."
                    "ERROR_USER_DISABLED" -> "User account disabled."
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many requests. Try again later."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "Operation not allowed."
                    else -> "Authentication error: ${exception.message}"
                }
            }
            is FirebaseFirestoreException -> {
                when (exception.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission denied. Please restart the app."
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        "Data not found."
                    FirebaseFirestoreException.Code.ABORTED ->
                        "Operation aborted. Retry?"
                    FirebaseFirestoreException.Code.OUT_OF_RANGE ->
                        "Request out of range."
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        "Authentication required. Please restart."
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        "Service unavailable. Check your internet."
                    else -> "Database error: ${exception.message}"
                }
            }
            is FirebaseException -> "Firebase error: ${exception.message}"
            else -> fallbackMessage
        }

        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        return message
    }

    fun isNetworkError(exception: Exception): Boolean {
        return exception is FirebaseNetworkException ||
                (exception is FirebaseFirestoreException &&
                        exception.code == FirebaseFirestoreException.Code.UNAVAILABLE)
    }
}