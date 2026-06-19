package com.neomud.server.platform

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class PlatformCredResult {
    data class Success(val userId: String, val role: String) : PlatformCredResult()
    data class AuthFailure(val message: String, val code: String = "INVALID_CREDENTIALS") : PlatformCredResult()
    data class ServiceError(val message: String) : PlatformCredResult()
}

/**
 * Calls the platform API to verify credentials or create anonymous sessions.
 * Used only for direct connections (dev/playtester bypassing the telnet router).
 */
class PlatformApiClient(private val apiUrl: String) {
    private val logger = LoggerFactory.getLogger(PlatformApiClient::class.java)
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun verifyCredentials(email: String, password: String): PlatformCredResult =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"email":${jsonString(email)},"password":${jsonString(password)}}"""
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$apiUrl/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                when (response.statusCode()) {
                    200 -> {
                        val obj = json.parseToJsonElement(response.body()).jsonObject
                        val user = obj["user"]?.jsonObject
                        val userId = user?.get("id")?.jsonPrimitive?.content
                        val role = user?.get("role")?.jsonPrimitive?.content
                        if (userId != null && role != null) {
                            PlatformCredResult.Success(userId, role)
                        } else {
                            PlatformCredResult.ServiceError("Unexpected response from platform")
                        }
                    }
                    401 -> {
                        val obj = json.parseToJsonElement(response.body()).jsonObject
                        val msg = obj["error"]?.jsonPrimitive?.content ?: "Invalid email or password"
                        PlatformCredResult.AuthFailure(msg, "INVALID_CREDENTIALS")
                    }
                    403 -> {
                        val obj = json.parseToJsonElement(response.body()).jsonObject
                        val code = obj["code"]?.jsonPrimitive?.content ?: "FORBIDDEN"
                        val msg = obj["error"]?.jsonPrimitive?.content ?: "Access denied"
                        PlatformCredResult.AuthFailure(msg, code)
                    }
                    else -> PlatformCredResult.ServiceError("Platform returned ${response.statusCode()}")
                }
            } catch (e: Exception) {
                logger.warn("Platform API call failed: ${e.message}")
                PlatformCredResult.ServiceError("Authentication service unavailable")
            }
        }

    suspend fun createGuestSession(): PlatformCredResult =
        withContext(Dispatchers.IO) {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create("$apiUrl/api/v1/auth/anonymous"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 200) {
                    val obj = json.parseToJsonElement(response.body()).jsonObject
                    val userId = obj["userId"]?.jsonPrimitive?.content
                    if (userId != null) {
                        PlatformCredResult.Success(userId, "GUEST")
                    } else {
                        PlatformCredResult.ServiceError("Unexpected response from platform")
                    }
                } else {
                    PlatformCredResult.ServiceError("Platform returned ${response.statusCode()}")
                }
            } catch (e: Exception) {
                logger.warn("Platform anonymous session failed: ${e.message}")
                PlatformCredResult.ServiceError("Authentication service unavailable")
            }
        }

    private fun jsonString(s: String): String =
        "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
