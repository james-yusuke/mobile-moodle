package org.moodle.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.moodle.data.local.AppPreferences
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {
    @Test
    fun `active account is stored and cleared only when it matches`() = runTest {
        val preferences = AppPreferences(ApplicationProvider.getApplicationContext())
        val accountId = UUID.randomUUID().toString()

        preferences.setActiveAccountId(accountId)
        assertEquals(accountId, preferences.activeAccountId.first())

        preferences.clearActiveAccountIfMatches("another-account")
        assertEquals(accountId, preferences.activeAccountId.first())

        preferences.clearActiveAccountIfMatches(accountId)
        assertNull(preferences.activeAccountId.first())
    }
}
