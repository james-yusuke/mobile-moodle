package org.moodle.core

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.moodle.core.security.PendingSso
import org.moodle.core.security.SsoProtocol
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class SsoProtocolTest {
    @Test
    fun `accepts a fresh callback with matching signature`() {
        val pending = PendingSso("https://example.edu/moodle", 42, 1_000)
        val signature = md5(pending.baseUrl + pending.passport)
        val result = SsoProtocol.validateCallback(
            Uri.parse("mobilemoodle://token=$signature:::public-token:::private-token"),
            pending,
            1_100,
        )
        assertEquals("public-token", result.token)
        assertEquals("private-token", result.privateToken)
    }

    @Test
    fun `rejects expired or forged callbacks`() {
        val pending = PendingSso("https://example.edu", 42, 1_000)
        assertThrows(IllegalArgumentException::class.java) {
            SsoProtocol.validateCallback(Uri.parse("mobilemoodle://token=bad:::token"), pending, 1_100)
        }
        val signature = md5(pending.baseUrl + pending.passport)
        assertThrows(IllegalArgumentException::class.java) {
            SsoProtocol.validateCallback(Uri.parse("mobilemoodle://token=$signature:::token"), pending, 2_000)
        }
    }

    private fun md5(value: String) = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.US_ASCII))
        .joinToString("") { "%02x".format(it) }
}
