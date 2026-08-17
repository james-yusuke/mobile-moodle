package org.moodle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.moodle.R
import org.moodle.core.model.ConversationType
import org.moodle.core.model.MessageSendState
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.MoodleMessageUser
import org.moodle.core.model.SiteAccount
import org.moodle.data.repository.conversationDraftKey
import org.moodle.data.repository.userDraftKey
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageListScreen(
    account: SiteAccount,
    conversations: List<MoodleConversation>,
    online: Boolean,
    onConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(account.id, account.capabilities.messages.canList) {
        if (account.capabilities.messages.canList && online) onRefresh()
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.messages), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (online) stringResource(R.string.message_sync_hint) else stringResource(R.string.offline_read_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = online && account.capabilities.messages.canList) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.sync))
                    }
                }
            }
            if (!account.capabilities.messages.canList) {
                item { PortalEmptyState(Icons.Outlined.AddComment, stringResource(R.string.messages_unavailable)) }
            } else if (conversations.isEmpty()) {
                item { PortalEmptyState(Icons.Outlined.AddComment, stringResource(R.string.empty_messages)) }
            }
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(conversation) { onConversation(conversation.id) }
            }
        }
        if (online && account.capabilities.messages.canSearchUsers && account.capabilities.messages.canStartConversation) {
            FloatingActionButton(
                onClick = onNewMessage,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) {
                Icon(Icons.Outlined.AddComment, stringResource(R.string.new_message))
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: MoodleConversation, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (conversation.unreadCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (conversation.type == ConversationType.Group) {
                Surface(Modifier.size(46.dp), shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Group, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            } else {
                InitialAvatar(conversation.name, 46.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.name,
                        Modifier.weight(1f),
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (conversation.latestMessageAt > 0) {
                        Text(
                            messageDate(conversation.latestMessageAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.latestMessagePreview.ifBlank { stringResource(R.string.no_message_preview) },
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (conversation.unreadCount > 0) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                conversation.unreadCount.coerceAtMost(99).toString(),
                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    account: SiteAccount,
    conversationId: Long,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val conversations by remember(account.id) { viewModel.conversations(account.id) }
        .collectAsStateWithLifecycle(emptyList())
    val conversation = conversations.firstOrNull { it.id == conversationId }
    val messages by remember(account.id, conversationId) { viewModel.messages(account.id, conversationId) }
        .collectAsStateWithLifecycle(emptyList())
    val draft by remember(account.id, conversationId) {
        viewModel.messageDraft(account.id, conversationDraftKey(conversationId))
    }.collectAsStateWithLifecycle(null)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val online = rememberNetworkAvailable()
    val lifecycleOwner = LocalLifecycleOwner.current
    var body by rememberSaveable(account.id, conversationId) { mutableStateOf("") }
    var draftLoaded by remember(account.id, conversationId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(draft?.body) {
        if (!draftLoaded) {
            body = draft?.body.orEmpty()
            draftLoaded = true
        }
    }
    LaunchedEffect(account.id, conversationId, lifecycleOwner, online) {
        if (!online) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshConversation(account.id, conversationId)
                delay(20_000)
            }
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(body, draftLoaded) {
        if (draftLoaded) {
            delay(500)
            viewModel.saveConversationDraft(account.id, conversationId, body)
        }
    }
    LaunchedEffect(state.messageSendState) {
        val sent = state.messageSendState as? MessageSendState.Sent
        if (sent?.conversationId == conversationId) {
            body = ""
            viewModel.resetMessageComposer()
        }
    }
    DisposableEffect(Unit) { onDispose(viewModel::resetMessageComposer) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InitialAvatar(conversation?.name ?: stringResource(R.string.messages), 36.dp)
                        Column {
                            Text(conversation?.name ?: stringResource(R.string.messages), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (online) stringResource(R.string.online) else stringResource(R.string.offline),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            MessageComposer(
                body,
                { body = it.take(4_000) },
                online && conversation?.canReply != false && account.capabilities.messages.canSend,
                state.messageSendState,
                { viewModel.sendMessage(account.id, conversationId, body) },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.size >= 50) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { viewModel.refreshConversation(account.id, conversationId, messages.size) }, enabled = online) {
                            Text(stringResource(R.string.load_older_messages))
                        }
                    }
                }
            }
            if (messages.isEmpty()) {
                item { PortalEmptyState(Icons.Outlined.AddComment, stringResource(R.string.empty_conversation)) }
            }
            items(messages, key = { it.id }) { message -> MessageBubble(message) }
        }
    }
}

@Composable
private fun MessageBubble(message: MoodleMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start) {
            if (!message.isMine) {
                Text(
                    message.senderName,
                    Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = if (message.isMine) {
                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                } else {
                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                },
                color = if (message.isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (message.isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        if (message.bodyHtml.isNotBlank()) {
                            AnnotatedString.fromHtml(
                                message.bodyHtml,
                                linkStyles = TextLinkStyles(
                                    style = SpanStyle(textDecoration = TextDecoration.Underline),
                                ),
                            )
                        } else {
                            AnnotatedString(message.bodyText)
                        },
                    )
                    Text(
                        messageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    body: String,
    onBodyChanged: (String) -> Unit,
    enabled: Boolean,
    sendState: MessageSendState,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!enabled) {
                Text(
                    stringResource(R.string.offline_send_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (sendState is MessageSendState.Failed) {
                Text(
                    sendState.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    body,
                    onBodyChanged,
                    Modifier.weight(1f),
                    enabled = enabled && sendState !is MessageSendState.Sending,
                    placeholder = { Text(stringResource(R.string.message_hint)) },
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                IconButton(
                    onClick = onSend,
                    enabled = enabled && body.isNotBlank() && sendState !is MessageSendState.Sending,
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    if (sendState is MessageSendState.Sending) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send_message))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(account: SiteAccount, viewModel: AppViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable(account.id) { mutableStateOf("") }
    var selected by remember { mutableStateOf<MoodleMessageUser?>(null) }
    DisposableEffect(Unit) { onDispose(viewModel::resetMessageComposer) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (selected != null) {
            NewMessageComposer(
                account,
                selected!!,
                state.messageSendState,
                viewModel,
                { selected = null },
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        query,
                        {
                            query = it
                            viewModel.searchMessageUsers(account.id, it)
                        },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.search_people)) },
                        leadingIcon = { Icon(Icons.Outlined.PersonSearch, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.searchMessageUsers(account.id, query) }),
                    )
                }
                if (state.messageSearchBusy) item {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
                if (query.length < 2) item {
                    Text(stringResource(R.string.search_people_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!state.messageSearchBusy && state.messageSearchResults.isEmpty()) item {
                    PortalEmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.no_people_found))
                }
                items(state.messageSearchResults, key = { it.id }) { user ->
                    Card(Modifier.fillMaxWidth().clickable { selected = user }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            InitialAvatar(user.fullName, 42.dp)
                            Text(user.fullName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewMessageComposer(
    account: SiteAccount,
    user: MoodleMessageUser,
    sendState: MessageSendState,
    viewModel: AppViewModel,
    onChangeRecipient: () -> Unit,
    modifier: Modifier,
) {
    val draft by remember(account.id, user.id) { viewModel.messageDraft(account.id, userDraftKey(user.id)) }
        .collectAsStateWithLifecycle(null)
    var body by rememberSaveable(account.id, user.id) { mutableStateOf("") }
    var loaded by remember(user.id) { mutableStateOf(false) }
    val online = rememberNetworkAvailable()
    LaunchedEffect(draft?.body) {
        if (!loaded) {
            body = draft?.body.orEmpty()
            loaded = true
        }
    }
    LaunchedEffect(body, loaded) {
        if (loaded) {
            delay(500)
            viewModel.saveUserDraft(account.id, user.id, body)
        }
    }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialAvatar(user.fullName, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.recipient), style = MaterialTheme.typography.labelSmall)
                    Text(user.fullName, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onChangeRecipient) { Text(stringResource(R.string.change)) }
            }
        }
        OutlinedTextField(
            body,
            { body = it.take(4_000) },
            Modifier.fillMaxWidth().weight(1f),
            label = { Text(stringResource(R.string.message_hint)) },
            enabled = sendState !is MessageSendState.Sending,
            minLines = 8,
        )
        if (sendState is MessageSendState.Failed) {
            Text(sendState.message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { viewModel.startConversation(account.id, user.id, body) },
            enabled = online && body.isNotBlank() && sendState !is MessageSendState.Sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (sendState is MessageSendState.Sending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.AutoMirrored.Outlined.Send, null)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.send_message))
        }
    }
}

private fun messageDate(epochSeconds: Long): String = DateFormat.getDateInstance(DateFormat.SHORT)
    .format(Date(epochSeconds * 1_000L))

private fun messageTime(epochSeconds: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT)
    .format(Date(epochSeconds * 1_000L))
