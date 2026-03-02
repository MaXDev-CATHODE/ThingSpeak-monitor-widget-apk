package com.thingspeak.monitor.feature.channel.data.repository

class RateLimitException(val retryAfterSeconds: Long) : Exception("ThingSpeak Rate Limit (429). Retry after $retryAfterSeconds seconds.")
