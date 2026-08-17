package org.moodle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moodle.core.network.MoodleUrl

class MoodleUrlTest {
    @Test
    fun `normalizes login URL while preserving Moodle subdirectory`() {
        assertEquals(
            "https://example.edu/moodle",
            MoodleUrl.normalize(" https://EXAMPLE.edu/moodle/login/index.php?next=1 "),
        )
    }

    @Test
    fun `adds HTTPS when scheme is omitted`() {
        assertEquals("https://example.edu", MoodleUrl.normalize("example.edu"))
    }

    @Test
    fun `rejects cleartext and embedded credentials`() {
        assertThrows(IllegalArgumentException::class.java) { MoodleUrl.normalize("http://example.edu") }
        assertThrows(IllegalArgumentException::class.java) { MoodleUrl.normalize("https://user:pass@example.edu") }
    }

    @Test
    fun `same site requires HTTPS host port and subpath`() {
        assertTrue(MoodleUrl.sameSite("https://example.edu/moodle", "https://example.edu/moodle/course/view.php?id=1"))
        assertFalse(MoodleUrl.sameSite("https://example.edu/moodle", "https://example.edu/other"))
        assertFalse(MoodleUrl.sameSite("https://example.edu", "https://other.example.edu"))
    }
}
