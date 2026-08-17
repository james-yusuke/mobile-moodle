package org.moodle.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moodle.data.local.CourseEntity
import org.moodle.data.local.ConversationEntity
import org.moodle.data.local.MessageEntity
import org.moodle.data.local.MoodleDatabase
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoodleDaoTest {
    private lateinit var database: MoodleDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MoodleDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `course caches are isolated by account`() = runTest {
        val dao = database.moodleDao()
        dao.replaceCourses("first", listOf(CourseEntity("first", 1, "A", "First", "", null, null)))
        dao.replaceCourses("second", listOf(CourseEntity("second", 1, "B", "Second", "", null, null)))
        assertEquals("First", dao.observeCourses("first").first().single().fullName)
        assertEquals("Second", dao.observeCourses("second").first().single().fullName)
    }

    @Test
    fun `messages and unread conversations are isolated by account`() = runTest {
        val dao = database.moodleDao()
        dao.replaceConversations(
            "first",
            listOf(ConversationEntity("first", 8, "Individual", "First user", "[]", "Hello", 10, 1, false, true)),
        )
        dao.replaceConversations(
            "second",
            listOf(ConversationEntity("second", 8, "Individual", "Second user", "[]", "Private", 11, 0, false, true)),
        )
        dao.upsertMessages(
            listOf(
                MessageEntity("first", 99, 8, 4, "First user", "Hello", "Hello", 10, false, false, true),
                MessageEntity("second", 99, 8, 5, "Second user", "Private", "Private", 11, false, true, true),
            ),
        )

        assertEquals("First user", dao.observeConversations("first").first().single().name)
        assertEquals("Second user", dao.observeConversations("second").first().single().name)
        assertEquals("Hello", dao.observeMessages("first", 8).first().single().bodyText)
        assertEquals("Private", dao.observeMessages("second", 8).first().single().bodyText)
    }
}
