package com.thingspeak.monitor.core.network

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor enforcing max 1 request per second to ThingSpeak API.
 *
 * ThingSpeak free tier limits requests to 1 req/s. Without this interceptor,
 * rapid, concurrent requests could trigger HTTP 429 (Too Many Requests).
 */
@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {

    companion object {
        /** ThingSpeak free tier: max 1 request per second + 50ms safety margin. */
        private const val RATE_LIMIT_INTERVAL_MS = 1_050L
        private const val TAG = "RateLimit"
    }

    private val lock = Any()

    @Volatile
    private var lastRequestTimeMs: Long = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimeMs

            if (elapsed < RATE_LIMIT_INTERVAL_MS) {
                val sleepMs = RATE_LIMIT_INTERVAL_MS - elapsed
                android.util.Log.d(TAG, "Rate limiting: sleeping for ${sleepMs}ms")
                try {
                    Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    android.util.Log.e(TAG, "Interrupted during rate limit sleep", e)
                }
            }

            lastRequestTimeMs = System.currentTimeMillis()
        }
        
        val response = chain.proceed(chain.request())
        
        if (response.code == 429) {
            android.util.Log.w(TAG, "Received 429 Too Many Requests even with interceptor!")
            val retryAfter = response.header("Retry-After")
            android.util.Log.w(TAG, "Retry-After header: $retryAfter")
        }
        
        return response
    }
}
