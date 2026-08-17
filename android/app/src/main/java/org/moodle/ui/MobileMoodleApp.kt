package org.moodle.ui

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collectLatest
import org.moodle.R
import org.moodle.core.model.SiteAccount

private const val ROUTE_SITES = "sites"
private const val ROUTE_ADD = "add"

@Composable
fun MobileMoodleApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val unknownError = stringResource(R.string.unknown_error)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppEvent.OpenAccount -> navController.openAccount(event.account)
                is AppEvent.OpenConversation -> navController.navigate(
                    "conversation/${event.accountId}/${event.conversationId}",
                ) { launchSingleTop = true }
                is AppEvent.OpenExternal -> runCatching {
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(event.url))
                }
                is AppEvent.OpenFile -> try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", event.file)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, event.mimeType ?: "application/octet-stream")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                } catch (_: Exception) {
                    snackbar.showSnackbar(unknownError)
                }
                AppEvent.ReturnToAccounts -> navController.navigate(ROUTE_SITES) {
                    popUpTo(ROUTE_SITES) { inclusive = true }
                }
            }
        }
    }

    MoodleTheme {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outerPadding ->
                Box(Modifier.fillMaxSize().padding(outerPadding)) {
                    NavHost(navController, startDestination = ROUTE_SITES) {
                        composable(ROUTE_SITES) {
                            PortalAccountListScreen(
                                accounts = accounts,
                                activeAccount = activeAccount,
                                onAdd = { navController.navigate(ROUTE_ADD) },
                                onOpen = viewModel::openAccount,
                            )
                        }
                        composable(ROUTE_ADD) {
                            PortalAddSiteScreen(
                                state = uiState,
                                onBack = { navController.popBackStack() },
                                onInspect = viewModel::inspectSite,
                                onLogin = viewModel::login,
                                onSso = viewModel::beginSso,
                                onDispose = viewModel::clearInspectedSite,
                            )
                        }
                        composable("native/{accountId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            accounts.firstOrNull { it.id == accountId }?.let { account ->
                                PortalAccountScreen(
                                    account = account,
                                    viewModel = viewModel,
                                    onSites = { navController.navigate(ROUTE_SITES) },
                                    onCourse = { navController.navigate("course/$accountId/$it") },
                                    onConversation = { navController.navigate("conversation/$accountId/$it") },
                                    onNewMessage = { navController.navigate("new-message/$accountId") },
                                )
                            } ?: LoadingBox()
                        }
                        composable("course/{accountId}/{courseId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            val courseId = entry.arguments?.getString("courseId")?.toLongOrNull() ?: return@composable
                            CourseScreen(
                                accountId,
                                courseId,
                                viewModel,
                                onBack = { navController.popBackStack() },
                                onAssignment = { assignmentId ->
                                    navController.navigate("assignment/$accountId/$courseId/$assignmentId")
                                },
                                onModule = { moduleId ->
                                    navController.navigate("module/$accountId/$courseId/$moduleId")
                                },
                            )
                        }
                        composable("module/{accountId}/{courseId}/{moduleId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            val courseId = entry.arguments?.getString("courseId")?.toLongOrNull() ?: return@composable
                            val moduleId = entry.arguments?.getString("moduleId")?.toLongOrNull() ?: return@composable
                            ModuleContentScreen(accountId, courseId, moduleId, uiState, viewModel) {
                                navController.popBackStack()
                            }
                        }
                        composable("assignment/{accountId}/{courseId}/{assignmentId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            val courseId = entry.arguments?.getString("courseId")?.toLongOrNull() ?: return@composable
                            val assignmentId = entry.arguments?.getString("assignmentId")?.toLongOrNull()
                                ?: return@composable
                            AssignmentScreen(accountId, courseId, assignmentId, uiState, viewModel) {
                                navController.popBackStack()
                            }
                        }
                        composable("conversation/{accountId}/{conversationId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            val conversationId = entry.arguments?.getString("conversationId")?.toLongOrNull()
                                ?: return@composable
                            accounts.firstOrNull { it.id == accountId }?.let { account ->
                                ConversationScreen(account, conversationId, viewModel) { navController.popBackStack() }
                            } ?: LoadingBox()
                        }
                        composable("new-message/{accountId}") { entry ->
                            val accountId = entry.arguments?.getString("accountId").orEmpty()
                            accounts.firstOrNull { it.id == accountId }?.let { account ->
                                NewMessageScreen(account, viewModel) { navController.popBackStack() }
                            } ?: LoadingBox()
                        }
                    }
                    if (uiState.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

private fun NavHostController.openAccount(account: SiteAccount) {
    navigate("native/${account.id}") {
        launchSingleTop = true
        popUpTo(ROUTE_SITES)
    }
}
