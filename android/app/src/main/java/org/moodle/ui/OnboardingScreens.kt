package org.moodle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PortalBrandMark(36.dp)
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(stringResource(R.string.add_site)) },
            )
        },
    ) { padding ->
        PortalBackground(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.fillMaxSize().widthIn(max = 940.dp),
                    contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = Color.Transparent,
                            shadowElevation = 6.dp,
                        ) {
                            Box(
                                Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(listOf(PortalNavy, PortalTealDark, Color(0xFF0A766B))),
                                ),
                            ) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(150.dp)
                                        .background(Color.White.copy(alpha = 0.05f), shape = androidx.compose.foundation.shape.CircleShape),
                                )
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PortalBrandMark(50.dp)
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        stringResource(R.string.onboarding_eyebrow).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF9EE3D6),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        stringResource(R.string.your_moodle),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color.White,
                                    )
                                    Text(
                                        stringResource(R.string.connected_sites_body),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White.copy(alpha = 0.78f),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        PortalSectionHeader(
                            stringResource(R.string.connected_sites),
                            supportingText = pluralStringResource(
                                R.plurals.connected_count,
                                accounts.size,
                                accounts.size,
                            ),
                        )
                    }
                    if (accounts.isEmpty()) item { PortalEmptyState(Icons.Outlined.Language, stringResource(R.string.no_sites)) }
                    items(accounts, key = { it.id }) { account ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onOpen(account) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = if (activeAccount?.id == account.id) 2.dp else 0.dp,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(17.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                InitialAvatar(account.siteName, 52.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        account.siteName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        account.fullName ?: account.username ?: account.baseUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    PortalStatusPill(
                                        if (account.connectionMode == ConnectionMode.NativeApi) "Native API"
                                        else stringResource(R.string.html_mode),
                                        icon = Icons.Outlined.Security,
                                        emphasized = activeAccount?.id == account.id,
                                    )
                                }
                                if (activeAccount?.id == account.id) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        stringResource(R.string.active_account),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_site)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                ),
            )
        },
    ) { padding ->
        PortalBackground(Modifier.padding(padding)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.fillMaxSize().widthIn(max = 680.dp),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            PortalEyebrow(stringResource(R.string.setup_site))
                            Text(
                                if (state.inspectedSite == null) stringResource(R.string.connect_to_moodle)
                                else stringResource(R.string.sign_in),
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Text(
                                stringResource(R.string.setup_supporting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PortalStatusPill(
                                stringResource(R.string.step_site_address),
                                emphasized = state.inspectedSite == null,
                            )
                            PortalStatusPill(
                                stringResource(R.string.step_sign_in),
                                emphasized = state.inspectedSite != null,
                            )
                        }
                    }
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(stringResource(R.string.secure_connection), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.secure_connection_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    val config = state.inspectedSite
                    if (config == null) {
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OutlinedTextField(
                                        url,
                                        { url = it },
                                        label = { Text(stringResource(R.string.site_url)) },
                                        placeholder = { Text(stringResource(R.string.site_url_hint)) },
                                        leadingIcon = { Icon(Icons.Outlined.Language, null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    Button(
                                        onClick = { onInspect(url) },
                                        enabled = url.isNotBlank() && !state.busy,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                    ) { Text(stringResource(R.string.continue_label)) }
                                }
                            }
                        }
                    } else {
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        InitialAvatar(config.siteName, 50.dp)
                                        Column(Modifier.weight(1f)) {
                                            Text(config.siteName, style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                config.canonicalUrl,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    if (config.connectionMode == ConnectionMode.NativeHtml) {
                                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Column(Modifier.padding(14.dp)) {
                                                Text(stringResource(R.string.html_mode_title), style = MaterialTheme.typography.titleMedium)
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
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                        OutlinedTextField(
                                            password,
                                            { password = it },
                                            label = { Text(stringResource(R.string.password)) },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                        Button(
                                            onClick = { onLogin(username, password); password = "" },
                                            enabled = username.isNotBlank() && password.isNotEmpty() && !state.busy,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        ) { Text(stringResource(R.string.sign_in)) }
                                    }
                                    if (config.connectionMode == ConnectionMode.NativeApi &&
                                        (config.browserSsoRequired || config.launchUrl != null)
                                    ) {
                                        OutlinedButton(
                                            onClick = onSso,
                                            enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        ) {
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
    }
}
