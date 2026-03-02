package com.thingspeak.monitor.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Interceptor that catches ThingSpeak's literal "-1" or "0" response bodies 
 * which indicate errors, even when the HTTP status code is 200 OK.
 * 
 * Replaces such responses with a 400 Bad Request to trigger standard error handling.
 */
class ThingSpeakErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        
        if (response.isSuccessful) {
            val bodyString = response.body?.string() ?: ""
            
            // ThingSpeak returns "-1" or "0" as body for many errors (e.g. invalid API key on write)
            // even with HTTP 200.
            if (bodyString.trim() == "-1" || bodyString.trim() == "0") {
                return response.newBuilder()
                    .code(400)
                    .message("ThingSpeak Error: $bodyString")
                    .body(bodyString.toResponseBody(response.body?.contentType()))
                    .build()
            }
            
            // Reconstruct the response body because we consumed it with .string()
            return response.newBuilder()
                .body(bodyString.toResponseBody(response.body?.contentType()))
                .build()
        }
        
        return response
    }
}
