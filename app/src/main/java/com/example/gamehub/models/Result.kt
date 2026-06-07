// models/Result.kt
package com.example.gamehub.models

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception, val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}