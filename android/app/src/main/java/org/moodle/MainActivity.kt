package org.moodle

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.moodle.ui.AppViewModel
import org.moodle.ui.MobileMoodleApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileMoodleApp(appViewModel) }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.takeIf { it.scheme == "mobilemoodle" }?.let(appViewModel::handleSso)
        appViewModel.handleMessageIntent(
            intent?.getStringExtra(EXTRA_ACCOUNT_ID),
            intent?.getLongExtra(EXTRA_CONVERSATION_ID, -1L)?.takeIf { it > 0 },
        )
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "org.moodle.extra.ACCOUNT_ID"
        const val EXTRA_CONVERSATION_ID = "org.moodle.extra.CONVERSATION_ID"
    }
}
