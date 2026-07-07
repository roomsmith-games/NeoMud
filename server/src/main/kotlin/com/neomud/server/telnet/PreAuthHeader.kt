package com.neomud.server.telnet

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PreAuthResult(val type: String, val userId: String, val characterName: String? = null)

/**
 * Parses and HMAC-verifies the pre-auth header sent by the telnet router.
 *
 * Without character name:   NEOMUD:<type>:<userId>:<hmac>
 * With character name:      NEOMUD:user:<userId>:<characterName>:<hmac>
 *
 * The HMAC covers everything before the last colon: "<type>:<userId>" or
 * "<type>:<userId>:<characterName>". `lastIndexOf(':')` always finds the hmac
 * boundary regardless of how many fields are present.
 */
object PreAuthHeader {
    private const val PREFIX = "NEOMUD:"

    fun verify(line: String, secret: String): PreAuthResult? {
        if (!line.startsWith(PREFIX)) return null
        val body = line.removePrefix(PREFIX)

        val lastColon = body.lastIndexOf(':')
        if (lastColon < 0) return null
        val payloadPart = body.substring(0, lastColon)
        val receivedHmac = body.substring(lastColon + 1)

        val expectedHmac = hmacSha256(payloadPart, secret)
        if (!MessageDigest.isEqual(expectedHmac.toByteArray(), receivedHmac.toByteArray())) return null

        // payloadPart = "<type>:<userId>" or "<type>:<userId>:<characterName>"
        val firstColon = payloadPart.indexOf(':')
        if (firstColon < 0) return null
        val type = payloadPart.substring(0, firstColon)
        val rest = payloadPart.substring(firstColon + 1)

        if (type != "user" && type != "guest") return null

        // Guests never have characters — treat the entire rest as userId.
        // Users may have an optional characterName appended after the userId.
        val userId: String
        val characterName: String?
        if (type == "user") {
            val secondColon = rest.indexOf(':')
            if (secondColon < 0) {
                userId = rest
                characterName = null
            } else {
                userId = rest.substring(0, secondColon)
                characterName = rest.substring(secondColon + 1).takeIf { it.isNotBlank() }
            }
        } else {
            userId = rest
            characterName = null
        }

        if (userId.isBlank()) return null

        return PreAuthResult(type = type, userId = userId, characterName = characterName)
    }

    private fun hmacSha256(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
