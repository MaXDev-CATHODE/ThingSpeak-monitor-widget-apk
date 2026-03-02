package com.thingspeak.monitor.core.error

import kotlin.coroutines.cancellation.CancellationException

/**
 * Standardized wrapper for asynchronous operation results.
 *
 * [Success] — operation completed successfully with data of type [T].
 * [Error]   — operation failed with a code and message.
 *
 * Loading state is managed SEPARATELY in ViewModel/UiState,
 * as it is a presentation concern, not a result of the operation.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Error(
        val code: Int,
        val message: String?,
        val cause: Throwable? = null
    ) : ApiResult<Nothing>
    data class Exception(val e: Throwable) : ApiResult<Nothing>
    data object Loading : ApiResult<Nothing>
}
/**
 * Safe execution of an asynchronous operation with correct exception mapping.
 *
 * - [CancellationException] is ALWAYS propagated (we don't catch coroutine cancellation).
 * - [retrofit2.HttpException] mapped to [ApiResult.Error] with HTTP code.
 * - [java.io.IOException] mapped to I/O error (network OR disk).
 * - Other exceptions mapped to a general error.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e // never catch coroutine cancellation
    } catch (e: retrofit2.HttpException) {
        val message = when (e.code()) {
            401 -> "Unauthorized: Check your API Key"
            403 -> "Forbidden: Private channel or invalid API Key"
            404 -> "Not Found: Channel does not exist"
            429 -> "Rate Limit Exceeded: Wait a moment"
            else -> "HTTP ${e.code()}: ${e.message()}"
        }
        // Placeholder for Timber.e(e, message)
        android.util.Log.e("ApiResult", message, e)
        ApiResult.Error(
            code = e.code(),
            message = message,
            cause = e,
        )
    } catch (e: java.io.IOException) {
        val message = "Network error: Verifiy your connection"
        android.util.Log.e("ApiResult", message, e)
        ApiResult.Error(
            code = -1, // Custom code for I/O
            message = message,
            cause = e,
        )
    } catch (e: Exception) {
        val message = e.localizedMessage ?: e.javaClass.simpleName
        android.util.Log.e("ApiResult", message, e)
        ApiResult.Error(
            code = -2, // Custom code for unknown exception
            message = message,
            cause = e,
        )
    }
}
