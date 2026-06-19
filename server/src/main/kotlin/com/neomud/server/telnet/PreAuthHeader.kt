package com.neomud.server.telnet

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PreAuthResult(val type: String, val userId: String)

/**
 * Parses and HMAC-verifies the pre-auth header sent by the telnet router.
 *
 * Expected line content (after the leading \x00 null byte is consumed):
 *   NEOMUD:<type>:<userId>:<hmac>
 *
 * The HMAC is SHA-256 of "<type>:<userId>" keyed by TELNET_PRE_AUTH_SECRET.
 * Constant-time comparison prevents timing attacks on the HMAC.
 */
object PreAuthHeader {
    private const val PREFIX = "NEOMUD:"

    fun verify(line: String, secret: String): PreAuthResult? {
        if (!line.startsWith(PREFIX)) return null
        val body = line.removePrefix(PREFIX)

        // body = "<type>:<userId>:<hmac>"
        // userId may not contain ':', so split at most into 3 parts from the left,
        // then treat the last segment as the hmac.
        val lastColon = body.lastIndexOf(':')
        if (lastColon < 0) return null
        val payloadPart = body.substring(0, lastColon)  // "<type>:<userId>"
        val receivedHmac = body.substring(lastColon + 1)

        val expectedHmac = hmacSha256(payloadPart, secret)
        if (!MessageDigest.isEqual(expectedHmac.toByteArray(), receivedHmac.toByteArray())) return null

        val firstColon = payloadPart.indexOf(':')
        if (firstColon < 0) return null
        val type = payloadPart.substring(0, firstColon)
        val userId = payloadPart.substring(firstColon + 1)

        if (type != "user" && type != "guest") return null
        if (userId.isBlank()) return null

        return PreAuthResult(type = type, userId = userId)
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
