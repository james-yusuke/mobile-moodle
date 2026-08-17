package org.moodle

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Read-only live test. All values come from ignored local-test.properties. */
class LiveHtmlSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun signsInAndRendersNativeHtmlDashboard() {
        val args = InstrumentationRegistry.getArguments()
        val siteUrl = args.getString("moodleSiteUrl").orEmpty()
        val username = args.getString("moodleUsername").orEmpty()
        val password = args.getString("moodlePassword").orEmpty()
        assumeTrue(siteUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank())

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.add_site),
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.site_url)).performTextInput(siteUrl)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.continue_label)).performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.html_mode_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.username)).performTextInput(username)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.password)).performTextInput(password)
        composeRule.onAllNodes(
            hasText(composeRule.activity.getString(R.string.sign_in)) and hasClickAction(),
        )
            .onFirst()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 45_000) {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.welcome_back))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(
            hasText(composeRule.activity.getString(R.string.courses)) and hasClickAction(),
        )
            .onFirst()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.empty_courses))
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
