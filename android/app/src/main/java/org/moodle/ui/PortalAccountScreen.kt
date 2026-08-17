package org.moodle.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.moodle.R
import org.moodle.core.model.AuthState
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.SiteAccount
import java.text.DateFormat
import java.util.Date

private enum class PortalDestination(val label: Int, val icon: ImageVector) {
    Home(R.string.home, Icons.Outlined.Home),
    Courses(R.string.courses, Icons.Outlined.School),
    Messages(R.string.messages, Icons.Outlined.AddComment),
    Calendar(R.string.calendar, Icons.Outlined.CalendarMonth),
    Notifications(R.string.notifications, Icons.Outlined.Notifications),
    Settings(R.string.settings, Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalAccountScreen(
    account: SiteAccount,
    viewModel: AppViewModel,
    onSites: () -> Unit,
    onCourse: (Long) -> Unit,
    onConversation: (Long) -> Unit,
    onNewMessage: () -> Unit,
) {
    var selected by remember(account.id) { mutableStateOf(PortalDestination.Home) }
    var accountMenu by remember { mutableStateOf(false) }
    val courses by remember(account.id) { viewModel.courses(account.id) }.collectAsStateWithLifecycle(emptyList())
    val grades by remember(account.id) { viewModel.grades(account.id) }.collectAsStateWithLifecycle(emptyList())
    val events by remember(account.id) { viewModel.events(account.id) }.collectAsStateWithLifecycle(emptyList())
    val notifications by remember(account.id) { viewModel.notifications(account.id) }
        .collectAsStateWithLifecycle(emptyList())
    val conversations by remember(account.id) { viewModel.conversations(account.id) }
        .collectAsStateWithLifecycle(emptyList())
    val showPreview by viewModel.showMessagePreview.collectAsStateWithLifecycle()
    val appState by viewModel.uiState.collectAsStateWithLifecycle()
    val online = rememberNetworkAvailable()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(account.id) { viewModel.sync(account.id) }

    val primaryDestinations = listOf(
        PortalDestination.Home,
        PortalDestination.Courses,
        PortalDestination.Messages,
        PortalDestination.Calendar,
    )
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(account.siteName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                account.fullName ?: account.username.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { selected = PortalDestination.Notifications }) {
                            Box {
                                Icon(Icons.Outlined.Notifications, stringResource(R.string.notifications))
                                if (notifications.any { !it.read }) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.sync(account.id) }, enabled = online) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.sync))
                        }
                        Box {
                            IconButton(onClick = { accountMenu = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    InitialAvatar(account.fullName ?: account.username ?: account.siteName, 32.dp)
                                    Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.account_menu))
                                }
                            }
                            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.switch_site)) },
                                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                                    onClick = { accountMenu = false; onSites() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings)) },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                    onClick = { accountMenu = false; selected = PortalDestination.Settings },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        primaryDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = selected == destination,
                                onClick = { selected = destination },
                                icon = { Icon(destination.icon, stringResource(destination.label)) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        primaryDestinations.forEach { destination ->
                            NavigationRailItem(
                                selected = selected == destination,
                                onClick = { selected = destination },
                                icon = { Icon(destination.icon, stringResource(destination.label)) },
                                label = { Text(stringResource(destination.label)) },
                            )
                        }
                    }
                    VerticalDivider(Modifier.fillMaxHeight())
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (!online) OfflineBanner(account.lastSyncEpochSeconds)
                    if (account.authState == AuthState.ReauthenticationRequired && selected == PortalDestination.Home) {
                        PortalReauthenticationScreen(account, viewModel, Modifier.weight(1f))
                    } else {
                        PullToRefreshBox(
                            isRefreshing = appState.busy,
                            onRefresh = { if (online) viewModel.sync(account.id) },
                            modifier = Modifier.weight(1f),
                        ) {
                            val showSkeleton = appState.busy && when (selected) {
                                PortalDestination.Home -> courses.isEmpty() && events.isEmpty()
                                PortalDestination.Courses -> courses.isEmpty()
                                PortalDestination.Messages -> conversations.isEmpty()
                                PortalDestination.Calendar -> events.isEmpty()
                                PortalDestination.Notifications -> notifications.isEmpty()
                                PortalDestination.Settings -> false
                            }
                            if (showSkeleton) {
                                PortalLoadingSkeleton()
                            } else AnimatedContent(
                                targetState = selected,
                                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                                label = "portal-destination",
                            ) { destination ->
                                when (destination) {
                                PortalDestination.Home -> PortalHomeScreen(
                                    account,
                                    courses,
                                    grades,
                                    events,
                                    notifications,
                                    conversations,
                                    onCourse,
                                    { selected = PortalDestination.Messages },
                                    Modifier.fillMaxSize(),
                                )
                                PortalDestination.Courses -> PortalCourseList(courses, onCourse, Modifier.fillMaxSize())
                                PortalDestination.Messages -> MessageListScreen(
                                    account,
                                    conversations,
                                    online,
                                    onConversation,
                                    onNewMessage,
                                    { viewModel.refreshConversations(account.id) },
                                    Modifier.fillMaxSize(),
                                )
                                PortalDestination.Calendar -> PortalEventList(events, Modifier.fillMaxSize())
                                PortalDestination.Notifications -> PortalNotificationList(
                                    notifications,
                                    { viewModel.markNotificationRead(account.id, it) },
                                    Modifier.fillMaxSize(),
                                )
                                PortalDestination.Settings -> PortalSettingsScreen(
                                    account,
                                    showPreview,
                                    viewModel::setMessagePreview,
                                    { viewModel.removeAccount(account.id) },
                                    {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    Modifier.fillMaxSize(),
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

@Composable
private fun PortalHomeScreen(
    account: SiteAccount,
    courses: List<MoodleCourse>,
    grades: List<MoodleGrade>,
    events: List<MoodleCalendarEvent>,
    notifications: List<MoodleNotification>,
    conversations: List<MoodleConversation>,
    onCourse: (Long) -> Unit,
    onMessages: () -> Unit,
    modifier: Modifier,
) {
    val unreadMessages = conversations.sumOf { it.unreadCount }
    val now = System.currentTimeMillis() / 1_000L
    val activeCourses = courses.count { it.endDate == null || it.endDate >= now }
    val upcomingEvents = events.count { it.startEpochSeconds >= now }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.labelLarge)
                    Text(
                        account.fullName ?: account.username ?: account.siteName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        account.lastSyncEpochSeconds?.let { stringResource(R.string.last_sync, portalDate(it)) }
                            ?: stringResource(R.string.never_synced),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewTile(
                    unreadMessages.toString(),
                    stringResource(R.string.unread_messages),
                    Icons.Outlined.AddComment,
                    Modifier.weight(1f).clickable(onClick = onMessages),
                )
                OverviewTile(
                    upcomingEvents.toString(),
                    stringResource(R.string.upcoming_schedule),
                    Icons.Outlined.CalendarMonth,
                    Modifier.weight(1f),
                )
                OverviewTile(
                    activeCourses.toString(),
                    stringResource(R.string.active_courses),
                    Icons.AutoMirrored.Outlined.MenuBook,
                    Modifier.weight(1f),
                )
            }
        }
        item { SectionTitle(stringResource(R.string.recent_courses), courses.size) }
        if (courses.isEmpty()) item { PortalEmptyState(Icons.Outlined.School, stringResource(R.string.empty_courses)) }
        items(courses.take(4), key = { "home-course:${it.id}" }) { PortalCourseCard(it, onCourse) }
        item { SectionTitle(stringResource(R.string.upcoming_schedule), events.size) }
        if (events.isEmpty()) item { PortalEmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_events)) }
        items(events.take(4), key = { "home-event:${it.id}" }) { PortalEventCard(it) }
        if (grades.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.grades), grades.size) }
            items(grades.take(4), key = { "home-grade:${it.courseId}:${it.itemId}" }) { PortalGradeCard(it) }
        }
        if (notifications.any { !it.read }) {
            item {
                Text(
                    pluralStringResource(
                        R.plurals.unread_updates_count,
                        notifications.count { !it.read },
                        notifications.count { !it.read },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OverviewTile(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PortalCourseList(courses: List<MoodleCourse>, onCourse: (Long) -> Unit, modifier: Modifier) {
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    val now = System.currentTimeMillis() / 1_000L
    val filtered = courses.filter { course ->
        (!activeOnly || course.endDate == null || course.endDate >= now) &&
            (query.isBlank() || course.fullName.contains(query, true) || course.shortName.contains(query, true))
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.courses), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_courses)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotBlank()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } }
                } else null,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(activeOnly, { activeOnly = true }, { Text(stringResource(R.string.active_courses)) })
                FilterChip(!activeOnly, { activeOnly = false }, { Text(stringResource(R.string.all_courses)) })
            }
        }
        if (filtered.isEmpty()) item { PortalEmptyState(Icons.Outlined.School, stringResource(R.string.empty_courses)) }
        items(filtered, key = { it.id }) { PortalCourseCard(it, onCourse) }
    }
}

@Composable
private fun PortalCourseCard(course: MoodleCourse, onCourse: (Long) -> Unit) {
    val accent = portalAccent(course.id)
    Card(
        Modifier.fillMaxWidth().clickable { onCourse(course.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(course.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (course.shortName.isNotBlank() && course.shortName != course.fullName) {
                    Text(course.shortName, style = MaterialTheme.typography.labelMedium, color = accent)
                }
                if (course.summaryHtml.isNotBlank()) {
                    Text(portalPlainText(course.summaryHtml), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                course.endDate?.let {
                    Text(stringResource(R.string.course_ends, portalDate(it)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PortalEventList(events: List<MoodleCalendarEvent>, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.calendar), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (events.isEmpty()) item { PortalEmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_events)) }
        items(events, key = { it.id }) { PortalEventCard(it) }
    }
}

@Composable
private fun PortalEventCard(event: MoodleCalendarEvent) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.name, fontWeight = FontWeight.SemiBold)
                Text(portalDate(event.startEpochSeconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (event.descriptionHtml.isNotBlank()) {
                    Text(portalPlainText(event.descriptionHtml), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PortalGradeCard(grade: MoodleGrade) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Text(grade.itemName, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(grade.gradeFormatted.ifBlank { grade.percentageFormatted }, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PortalNotificationList(
    notifications: List<MoodleNotification>,
    onOpen: (MoodleNotification) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.notifications), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (notifications.isEmpty()) item { PortalEmptyState(Icons.Outlined.Notifications, stringResource(R.string.empty_notifications)) }
        items(notifications, key = { it.id }) { notification ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(notification) }) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape)
                            .background(if (notification.read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(notification.subject, fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold)
                        Text(portalPlainText(notification.fullMessageHtml), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(portalDate(notification.createdAt), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalSettingsScreen(
    account: SiteAccount,
    showPreview: Boolean,
    onPreviewChanged: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEnableNotifications: () -> Unit,
    modifier: Modifier,
) {
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialAvatar(account.fullName ?: account.username ?: account.siteName, 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(account.fullName ?: account.username.orEmpty(), fontWeight = FontWeight.SemiBold)
                            Text(account.siteName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(account.baseUrl, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (account.connectionMode == ConnectionMode.NativeApi) "Native API" else stringResource(R.string.html_mode),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    account.moodleVersion?.let { Text(stringResource(R.string.moodle_version, it)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.message_preview_setting), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.message_preview_setting_body), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(showPreview, onPreviewChanged)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) item {
            OutlinedButton(onClick = onEnableNotifications, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Notifications, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.enable_notifications))
            }
        }
        item {
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.remove_account))
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(stringResource(R.string.remove_account_confirm)) },
        confirmButton = { TextButton(onClick = { confirm = false; onRemove() }) { Text(stringResource(R.string.remove)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PortalReauthenticationScreen(account: SiteAccount, viewModel: AppViewModel, modifier: Modifier) {
    var username by remember(account.id) { mutableStateOf(account.username.orEmpty()) }
    var password by remember(account.id) { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.session_expired), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.session_expired_body))
        OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.username)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text(stringResource(R.string.password)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.reauthenticate(account.id, username, password); password = "" },
            enabled = username.isNotBlank() && password.isNotBlank(),
        ) { Text(stringResource(R.string.sign_in)) }
    }
}

@Composable
private fun OfflineBanner(lastSync: Long?) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(
            lastSync?.let { stringResource(R.string.offline_cached_at, portalDate(it)) }
                ?: stringResource(R.string.offline_no_cache),
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "M"
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = portalAccent(name.hashCode().toLong())) {
        Box(contentAlignment = Alignment.Center) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PortalEmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.outline)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PortalLoadingSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) { index ->
            Card(
                Modifier.fillMaxWidth().height(if (index == 0) 112.dp else 84.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {}
        }
    }
}

private fun portalAccent(seed: Long): Color {
    val colors = listOf(
        Color(0xFF006B5F),
        Color(0xFF355F8A),
        Color(0xFF7B526B),
        Color(0xFF7A5D00),
        Color(0xFF496647),
    )
    return colors[((seed % colors.size + colors.size) % colors.size).toInt()]
}

private fun portalPlainText(html: String): String = org.jsoup.Jsoup.parseBodyFragment(html).text().trim()

private fun portalDate(epochSeconds: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(epochSeconds * 1_000L))
