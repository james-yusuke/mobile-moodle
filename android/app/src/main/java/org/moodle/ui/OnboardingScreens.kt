package org.moodle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.moodle.R
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.SiteAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalAccountListScreen(
    accounts: List<SiteAccount>,
    activeAccount: SiteAccount?,
    onAdd: () -> Unit,
    onOpen: (SiteAccount) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.add_site)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.School, null, Modifier.size(42.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.your_moodle), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.connected_sites_body))
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.connected_sites), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (accounts.isEmpty()) item { PortalEmptyState(Icons.Outlined.Language, stringResource(R.string.no_sites)) }
            items(accounts, key = { it.id }) { account ->
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(account) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InitialAvatar(account.siteName, 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(account.siteName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(account.fullName ?: account.username ?: account.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (account.connectionMode == ConnectionMode.NativeApi) "Native API" else stringResource(R.string.html_mode),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (activeAccount?.id == account.id) {
                            Icon(Icons.Outlined.CheckCircle, stringResource(R.string.active_account), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalAddSiteScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onInspect: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onSso: () -> Unit,
    onDispose: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { password = ""; onDispose() } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_site)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(stringResource(R.string.secure_connection), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.secure_connection_body), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            val config = state.inspectedSite
            if (config == null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(stringResource(R.string.connect_to_moodle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                url,
                                { url = it },
                                label = { Text(stringResource(R.string.site_url)) },
                                placeholder = { Text(stringResource(R.string.site_url_hint)) },
                                leadingIcon = { Icon(Icons.Outlined.Language, null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { onInspect(url) },
                                enabled = url.isNotBlank() && !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.continue_label)) }
                        }
                    }
                }
            } else {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                InitialAvatar(config.siteName, 46.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(config.siteName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text(config.canonicalUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (config.connectionMode == ConnectionMode.NativeHtml) {
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(stringResource(R.string.html_mode_title), fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.html_mode_body), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            if (config.showLoginForm && !config.browserSsoRequired) {
                                OutlinedTextField(
                                    username,
                                    { username = it },
                                    label = { Text(stringResource(R.string.username)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    password,
                                    { password = it },
                                    label = { Text(stringResource(R.string.password)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = { onLogin(username, password); password = "" },
                                    enabled = username.isNotBlank() && password.isNotEmpty() && !state.busy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.sign_in)) }
                            }
                            if (config.connectionMode == ConnectionMode.NativeApi &&
                                (config.browserSsoRequired || config.launchUrl != null)
                            ) {
                                OutlinedButton(onClick = onSso, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.sign_in_browser))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
