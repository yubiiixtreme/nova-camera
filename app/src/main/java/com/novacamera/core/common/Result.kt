package com.novacamera.core.common

/** Simple Result wrapper used across data/domain layers. */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val throwable: Throwable, val message: String? = throwable.message) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}

inline fun <T> runCatchingApp(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppResult.Error(e)
}
