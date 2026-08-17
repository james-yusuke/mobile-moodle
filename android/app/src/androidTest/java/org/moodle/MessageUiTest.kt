package org.moodle

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.ConversationType
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleConversationMember
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.SiteAccount
import org.moodle.core.model.SiteCapabilities
import org.moodle.ui.MessageListScreen
import org.moodle.ui.MessageBubble
import org.moodle.ui.MessageDateSeparator
import org.moodle.ui.MoodleTheme
import java.text.DateFormat
import java.util.Date

class MessageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun conversationListShowsUnreadStateAndNewMessageAction() {
        val account = SiteAccount(
            id = "account",
            baseUrl = "https://example.edu",
            siteName = "Campus",
            username = "student",
            userId = 10,
            fullName = "Student",
            connectionMode = ConnectionMode.NativeApi,
            capabilities = SiteCapabilities(
                functions = setOf(
                    "core_message_get_conversations",
                    "core_message_get_conversation_messages",
                    "core_message_message_search_users",
                    "core_message_send_messages_to_conversation",
                    "core_message_send_instant_messages",
                ),
            ),
        )
        val conversation = MoodleConversation(
            id = 8,
            type = ConversationType.Individual,
            name = "Ada Lovelace",
            members = listOf(MoodleConversationMember(20, "Ada Lovelace", false, true)),
            latestMessagePreview = "Welcome",
            latestMessageAt = 10,
            unreadCount = 2,
            isFavourite = false,
            canReply = true,
        )

        composeRule.setContent {
            MoodleTheme {
                MessageListScreen(account, listOf(conversation), true, {}, {}, {}, androidx.compose.ui.Modifier)
            }
        }

        composeRule.onNodeWithText("Ada Lovelace").assertExists()
        composeRule.onNodeWithText("2").assertExists()
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.new_message)
        composeRule.onNodeWithContentDescription(label).assertExists()
    }

    @Test
    fun darkConversationRendersDateSeparatorAndReadableBubble() {
        val createdAt = 1_700_000_000L
        val message = MoodleMessage(
            id = 1,
            conversationId = 8,
            senderId = 20,
            senderName = "Ada Lovelace",
            bodyText = "A carefully written long message for the course project.",
            bodyHtml = "",
            createdAt = createdAt,
            isMine = false,
            isRead = true,
        )

        composeRule.setContent {
            MoodleTheme(darkTheme = true) {
                Column {
                    MessageDateSeparator(createdAt)
                    MessageBubble(message)
                }
            }
        }

        composeRule.onNodeWithText("Ada Lovelace").assertExists()
        composeRule.onNodeWithText(message.bodyText).assertExists()
        composeRule.onNodeWithText(
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(createdAt * 1_000L)),
        ).assertExists()
    }
}
