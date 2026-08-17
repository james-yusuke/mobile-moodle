package org.moodle.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.moodle.core.model.AssignmentPayload
import org.moodle.core.model.MoodleAssignment

class AssignmentPayloadTest {
    @Test
    fun `encodes online text and draft file using Moodle plugin keys`() {
        val assignment = MoodleAssignment(7, 2, 9, "Work", "", null, null, true, true, true)
        val fields = AssignmentPayload.build(assignment, "hello", 123)
        assertEquals("7", fields["assignmentid"])
        assertEquals("hello", fields["plugindata[onlinetext_editor][text]"])
        assertEquals("123", fields["plugindata[files_filemanager]"])
    }

    @Test
    fun `does not send disabled plugins`() {
        val assignment = MoodleAssignment(7, 2, 9, "Work", "", null, null, false, false, false)
        val fields = AssignmentPayload.build(assignment, "ignored", 123)
        assertFalse(fields.keys.any { it.startsWith("plugindata") })
    }
}
