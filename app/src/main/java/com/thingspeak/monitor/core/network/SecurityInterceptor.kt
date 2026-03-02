package com.thingspeak.monitor.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that masks sensitive information (like api_key) in URLs
 * to prevent them from appearing in plaintext in logs or diagnostic tools.
 */
class SecurityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url
        
        // If the URL contains an api_key, we don't necessarily want to MASK IT in the actual request
        // sent to the server (obviously), but we want to ensure that any downstream LOGGING 
        // intercepts a request that doesn't have it, or we handle it here.
        
        // Note: OkHttp HttpLoggingInterceptor logs the URL of the request it receives.
        // To mask it in logs without breaking the request, we would need to wrap the URL or 
        // use a custom Logger in HttpLoggingInterceptor.
        
        // HOWEVER, a more robust way for "Security Audit" purposes is to ensure 
        // sensitive keys are NOT in the URL if the API supports it. 
        // Since ThingSpeak supports api_key in headers, we can move it there!
        
        val apiKey = url.queryParameter("api_key")
        
        return if (apiKey != null) {
            val newUrl = url.newBuilder()
                .removeAllQueryParameters("api_key")
                .build()
                
            val newRequest = originalRequest.newBuilder()
                .url(newUrl)
                .addHeader("X-THINGSPEAKAPIKEY", apiKey)
                .build()
                
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
