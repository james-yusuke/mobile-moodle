package org.moodle

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moodle.data.local.MoodleDatabase
import org.moodle.di.MIGRATION_1_2
import org.moodle.di.MIGRATION_2_3

@RunWith(AndroidJUnit4::class)
class MoodleMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MoodleDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun webFallbackAccountBecomesReauthenticationRequiredNativeHtml() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                """INSERT INTO site_accounts
                    (id, baseUrl, siteName, username, userId, fullName, connectionMode, functionsJson, lastSyncEpochSeconds, isActive)
                    VALUES ('account', 'https://example.edu', 'Campus', 'student', NULL, NULL, 'WebFallback', '[]', NULL, 1)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2).use { database ->
            database.query("SELECT connectionMode, authState, htmlFeaturesJson FROM site_accounts WHERE id='account'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("NativeHtml", cursor.getString(0))
                assertEquals("ReauthenticationRequired", cursor.getString(1))
                assertEquals("[]", cursor.getString(2))
            }
        }
    }

    @Test
    fun versionTwoAddsAccountScopedMessageTables() {
        helper.createDatabase(DB_NAME, 2).close()

        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).use { database ->
            database.execSQL(
                """INSERT INTO conversations
                    (accountId, conversationId, type, name, membersJson, latestMessagePreview,
                     latestMessageAt, unreadCount, isFavourite, canReply)
                    VALUES ('account', 7, 'Individual', 'Student', '[]', 'Hello', 10, 1, 0, 1)
                """.trimIndent(),
            )
            database.query("SELECT unreadCount FROM conversations WHERE accountId='account'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private companion object { const val DB_NAME = "migration-test" }
}
