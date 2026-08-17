package org.moodle

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun accountPickerOffersSiteAddition() {
        val label = composeRule.activity.getString(R.string.add_site)
        composeRule.onNodeWithText(label, useUnmergedTree = true).assertExists()
    }
}
