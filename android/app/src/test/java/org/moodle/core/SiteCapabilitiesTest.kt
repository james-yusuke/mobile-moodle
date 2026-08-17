package org.moodle.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moodle.core.model.SiteCapabilities
import org.moodle.core.model.HtmlFeature

class SiteCapabilitiesTest {
    @Test
    fun `features are derived from functions advertised by Moodle`() {
        val capabilities = SiteCapabilities(
            setOf(
                "core_enrol_get_users_courses",
                "core_course_get_contents",
                "mod_assign_get_assignments",
                "mod_assign_save_submission",
                "tool_mobile_get_autologin_key",
            ),
        )
        assertTrue(capabilities.courses)
        assertTrue(capabilities.contents)
        assertTrue(capabilities.assignmentSubmission)
        assertTrue(capabilities.autoLogin)
        assertFalse(capabilities.notifications)
    }

    @Test
    fun `html features enable reads but never assignment submission`() {
        val capabilities = SiteCapabilities(
            htmlFeatures = setOf(HtmlFeature.Courses, HtmlFeature.Contents, HtmlFeature.AssignmentsRead, HtmlFeature.Notifications),
        )
        assertTrue(capabilities.courses)
        assertTrue(capabilities.contents)
        assertTrue(capabilities.assignments)
        assertTrue(capabilities.notifications)
        assertFalse(capabilities.assignmentSubmission)
    }

    @Test
    fun `message actions are enabled independently from advertised functions`() {
        val capabilities = SiteCapabilities(
            functions = setOf(
                "core_message_get_conversations",
                "core_message_get_conversation_messages",
                "core_message_message_search_users",
            ),
        )

        assertTrue(capabilities.messages.canList)
        assertTrue(capabilities.messages.canRead)
        assertTrue(capabilities.messages.canSearchUsers)
        assertFalse(capabilities.messages.canSend)
        assertFalse(capabilities.messages.canMarkRead)
    }
}
