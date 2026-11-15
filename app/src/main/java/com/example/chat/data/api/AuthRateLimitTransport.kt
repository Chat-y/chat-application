package com.example.chat.data.api

import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class AuthRateLimitException(val retryAfterSeconds: Int, message: String) : Exception(message)

/** Honors only an actual429 + Retry-After. Never retries a POST. */
class AuthRateLimitTransport(private val delegate: ApiHeaderHttpTransport,
    private val now: () -> Instant = { Instant.now() }) : ApiHttpTransport {
    override fun execute(method: String,url: String,headers: Map<String,String>,body: String?): Pair<Int,String> {
        val response = delegate.executeWithHeaders(method,url,headers,body)
        if (response.code == 429) {
            val wait = parseRetryAfter(response.retryAfter, now())
            if (wait != null) {
                val message = runCatching {
                    val root = JSONObject(response.body)
                    root.optJSONObject("detail")?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: root.optString("detail").takeIf { it.isNotBlank() }
                }.getOrNull() ?: "Too many attempts. Please wait before trying again."
                throw AuthRateLimitException(wait,message)
            }
        }
        return response.code to response.body
    }
    companion object {
        fun parseRetryAfter(raw: String?, now: Instant): Int? {
            if (raw.isNullOrBlank()) return null
            val seconds = raw.trim().toLongOrNull() ?: runCatching {
                java.time.Duration.between(now,ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).seconds
            }.getOrNull() ?: return null
            return seconds.coerceIn(0,86_400).toInt()
        }
    }
}
