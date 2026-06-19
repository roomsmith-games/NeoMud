package com.neomud.server.telnet

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreAuthHeaderTest {
    private val secret = "test-secret-32-bytes-of-entropy!"

    private fun hmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun validHeader(type: String, userId: String): String {
        val hmac = hmac("$type:$userId")
        return "NEOMUD:$type:$userId:$hmac"
    }

    @Test fun verifiesValidUserHeader() {
        val result = PreAuthHeader.verify(validHeader("user", "user-abc123"), secret)
        assertNotNull(result)
        assertEquals("user", result.type)
        assertEquals("user-abc123", result.userId)
    }

    @Test fun verifiesValidGuestHeader() {
        val result = PreAuthHeader.verify(validHeader("guest", "anon-deadbeef"), secret)
        assertNotNull(result)
        assertEquals("guest", result.type)
        assertEquals("anon-deadbeef", result.userId)
    }

    @Test fun returnsNullOnWrongSecret() {
        val header = validHeader("user", "user-abc123")
        assertNull(PreAuthHeader.verify(header, "wrong-secret"))
    }

    @Test fun returnsNullOnMissingNeomudPrefix() {
        val hmac = hmac("user:uid")
        assertNull(PreAuthHeader.verify("INVALID:user:uid:$hmac", secret))
    }

    @Test fun returnsNullOnTooFewParts() {
        assertNull(PreAuthHeader.verify("NEOMUD:user:onlyonecolon", secret))
    }

    @Test fun returnsNullOnEmptyUserId() {
        val hmac = hmac("user:")
        assertNull(PreAuthHeader.verify("NEOMUD:user::$hmac", secret))
    }

    @Test fun returnsNullOnUnknownType() {
        val hmac = hmac("admin:uid")
        assertNull(PreAuthHeader.verify("NEOMUD:admin:uid:$hmac", secret))
    }

    @Test fun returnsNullOnTamperedUserId() {
        val header = validHeader("user", "user-abc123")
        val tampered = header.replace("user-abc123", "user-attacker")
        assertNull(PreAuthHeader.verify(tampered, secret))
    }

    @Test fun returnsNullOnTamperedType() {
        val header = validHeader("guest", "anon-xyz")
        val tampered = header.replace("NEOMUD:guest:", "NEOMUD:user:")
        assertNull(PreAuthHeader.verify(tampered, secret))
    }

    @Test fun preservesColonInUserId() {
        // anon: prefix for guest userIds contains a colon
        val userId = "anon:0123456789abcdef"
        val result = PreAuthHeader.verify(validHeader("guest", userId), secret)
        assertNotNull(result)
        assertEquals(userId, result.userId)
    }
}
