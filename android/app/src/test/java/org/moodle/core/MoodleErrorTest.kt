package org.moodle.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moodle.core.model.MoodleError

class MoodleErrorTest {
    @Test
    fun `authentication failures require login`() {
        listOf(
            "session_expired",
            "invalidtoken",
            "requireloginerror",
            "invalidsesskey",
            "sessionipnomatch",
            "session_context_missing",
        ).forEach { code ->
            assertTrue(code, MoodleError(code, "expired").requiresReauthentication)
        }
    }

    @Test
    fun `ordinary failures stay on the current screen`() {
        assertFalse(MoodleError("network_error", "offline").requiresReauthentication)
        assertFalse(MoodleError("nopermissions", "forbidden").requiresReauthentication)
    }
}
