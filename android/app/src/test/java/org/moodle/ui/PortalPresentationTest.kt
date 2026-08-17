package org.moodle.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.moodle.core.model.ConversationType
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleCourse

class PortalPresentationTest {
    @Test
    fun dashboardSnapshotSelectsNearestUpcomingEventAndCountsActiveData() {
        val now = 1_000L
        val snapshot = buildPortalDashboardSnapshot(
            courses = listOf(
                MoodleCourse(1, "A", "Active", "", null, null),
                MoodleCourse(2, "B", "Ended", "", null, 999),
            ),
            events = listOf(
                MoodleCalendarEvent(1, "Past", "", 900, null, null),
                MoodleCalendarEvent(2, "Later", "", 1_300, null, null),
                MoodleCalendarEvent(3, "Next", "", 1_100, null, null),
            ),
            conversations = listOf(
                MoodleConversation(1, ConversationType.Individual, "Tutor", emptyList(), "", 0, 3, false, true),
            ),
            nowEpochSeconds = now,
        )

        assertEquals(1, snapshot.activeCourseCount)
        assertEquals(2, snapshot.upcomingEventCount)
        assertEquals(3, snapshot.unreadMessageCount)
        assertEquals("Next", snapshot.nextEvent?.name)
    }

    @Test
    fun dashboardSnapshotHasNoNextEventWhenOnlyPastEventsExist() {
        val snapshot = buildPortalDashboardSnapshot(
            courses = emptyList(),
            events = listOf(MoodleCalendarEvent(1, "Past", "", 10, null, null)),
            conversations = emptyList(),
            nowEpochSeconds = 20,
        )

        assertNull(snapshot.nextEvent)
    }

    @Test
    fun coursePaletteIndexIsStableForPositiveAndNegativeSeeds() {
        assertEquals(2, coursePaletteIndex(7, 5))
        assertEquals(3, coursePaletteIndex(-2, 5))
        assertEquals(coursePaletteIndex(42), coursePaletteIndex(42))
    }
}
