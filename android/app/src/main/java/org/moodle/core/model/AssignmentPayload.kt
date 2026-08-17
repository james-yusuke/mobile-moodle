package org.moodle.core.model

object AssignmentPayload {
    fun build(
        assignment: MoodleAssignment,
        onlineText: String,
        draftItemId: Long?,
    ): Map<String, String> = buildMap {
        put("assignmentid", assignment.id.toString())
        if (assignment.allowsOnlineText) {
            put("plugindata[onlinetext_editor][text]", onlineText)
            put("plugindata[onlinetext_editor][format]", "1")
            put("plugindata[onlinetext_editor][itemid]", "0")
        }
        if (assignment.allowsFiles && draftItemId != null) {
            put("plugindata[files_filemanager]", draftItemId.toString())
        }
    }
}
