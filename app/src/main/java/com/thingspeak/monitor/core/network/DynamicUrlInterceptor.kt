package com.thingspeak.monitor.core.network

import com.thingspeak.monitor.core.datastore.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Intercepts every request and replaces the host with the one defined in [AppPreferences].
 */
@Singleton
class DynamicUrlInterceptor @Inject constructor(
    private val appPreferences: AppPreferences
) : Interceptor {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var cachedUrl: HttpUrl? = "https://api.thingspeak.com/".toHttpUrlOrNull()

    init {
        // Subscribe to URL changes in the background to always have the latest value in memory (thread-safe via volatile)
        scope.launch {
            appPreferences.observeServerUrl().collect { url ->
                cachedUrl = url.toHttpUrlOrNull()
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        cachedUrl?.let { newUrl ->
            val newFullUrl = request.url.newBuilder()
                .scheme(newUrl.scheme)
                .host(newUrl.host)
                .port(newUrl.port)
                .build()
            
            request = request.newBuilder()
                .url(newFullUrl)
                .build()
        }

        return chain.proceed(request)
    }
}
