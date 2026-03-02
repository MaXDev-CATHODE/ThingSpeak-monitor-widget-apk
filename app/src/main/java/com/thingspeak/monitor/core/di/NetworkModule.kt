package com.thingspeak.monitor.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.thingspeak.monitor.BuildConfig
import com.thingspeak.monitor.core.network.RateLimitInterceptor
import com.thingspeak.monitor.core.network.SecurityInterceptor
import com.thingspeak.monitor.core.network.ThingSpeakApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module configuring the network layer (Retrofit + OkHttp).
 *
 * Installed in [SingletonComponent] — one instance per app lifecycle.
 * Includes [RateLimitInterceptor] enforcing max 1 req/s to ThingSpeak.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://api.thingspeak.com/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        rateLimiter: RateLimitInterceptor,
        dynamicUrlInterceptor: com.thingspeak.monitor.core.network.DynamicUrlInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(com.thingspeak.monitor.core.network.ThingSpeakErrorInterceptor())
            .addInterceptor(SecurityInterceptor())
            .addInterceptor(rateLimiter)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideThingSpeakApi(retrofit: Retrofit): ThingSpeakApiService {
        return retrofit.create(ThingSpeakApiService::class.java)
    }
}
