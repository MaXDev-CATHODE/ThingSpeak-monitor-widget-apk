package com.thingspeak.monitor.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

class SecurityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url

        val rawApiKey = url.queryParameter("api_key") ?: return chain.proceed(originalRequest)

        val sanitizedKey = sanitizeApiKey(rawApiKey)

        if (sanitizedKey.isEmpty()) {
            Log.w("SecurityInterceptor", "API key for channel is invalid after sanitization, removing from request")
            val cleanedUrl = url.newBuilder()
                .removeAllQueryParameters("api_key")
                .build()
            return chain.proceed(originalRequest.newBuilder().url(cleanedUrl).build())
        }

        val newUrl = url.newBuilder()
            .removeAllQueryParameters("api_key")
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .addHeader("THINGSPEAKAPIKEY", sanitizedKey)
            .build()

        return chain.proceed(newRequest)
    }

    private fun sanitizeApiKey(key: String): String {
        val sanitized = key.replace(Regex("[\\r\\n\\t]"), "").trim()
        if (sanitized.length > 256) {
            Log.w(TAG, "API key truncated from ${sanitized.length} to 256 characters")
            return sanitized.take(256)
        }
        return sanitized
    }

    companion object {
        private const val TAG = "SecurityInterceptor"
    }
}
