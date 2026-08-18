package org.moodle

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.SiteAccount
import org.moodle.core.model.SiteCapabilities
import org.moodle.ui.MoodleTheme
import org.moodle.ui.PortalAccountPanel
import org.moodle.ui.PortalDestination
import org.moodle.ui.PortalNavigationDock

class PortalChromeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val account = SiteAccount(
        id = "account",
        baseUrl = "https://example.edu",
        siteName = "Sample University",
        username = "student",
        userId = 10,
        fullName = "Moodle Student",
        connectionMode = ConnectionMode.NativeApi,
        capabilities = SiteCapabilities(),
    )

    @Test
    fun accountPanelShowsIdentityAndComfortableActions() {
        composeRule.setContent {
            MoodleTheme {
                PortalAccountPanel(account = account, onSites = {}, onSettings = {})
            }
        }

        composeRule.onNodeWithTag("account_panel").assertIsDisplayed()
        composeRule.onNodeWithText("Moodle Student").assertIsDisplayed()
        composeRule.onNodeWithText("Sample University").assertIsDisplayed()
        composeRule.onNodeWithTag("account_panel_sites").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("account_panel_settings").assertIsDisplayed().performClick()
    }

    @Test
    fun navigationDockExposesClearSelectedState() {
        composeRule.setContent {
            MoodleTheme {
                var selected by remember { mutableStateOf(PortalDestination.Home) }
                PortalNavigationDock(
                    destinations = listOf(
                        PortalDestination.Home,
                        PortalDestination.Courses,
                        PortalDestination.Messages,
                        PortalDestination.Calendar,
                    ),
                    selected = selected,
                    unreadMessages = 2,
                    onSelected = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag("portal_navigation_dock").assertIsDisplayed()
        composeRule.onNodeWithTag("portal_tab_home").assertIsSelected()
        composeRule.onNodeWithTag("portal_tab_messages").performClick().assertIsSelected()
        val messages = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.messages)
        composeRule.onNodeWithText(messages).assertIsDisplayed()
    }
}
