package org.moodle.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        val expanded = maxWidth >= 840.dp
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (wide) PortalBrandMark(38.dp)
                            Column {
                                Text(
                                    account.siteName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    account.fullName ?: account.username.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { selected = PortalDestination.Notifications }) {
                            BadgedBox(
                                badge = {
                                    val unread = notifications.count { !it.read }
                                    if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
                                }
                            ) {
                                Icon(Icons.Outlined.Notifications, stringResource(R.string.notifications))
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                    ),
                )
            },
            bottomBar = {
                if (!wide) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = 8.dp,
                            tonalElevation = 2.dp,
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                modifier = Modifier.height(72.dp),
                            ) {
                                primaryDestinations.forEach { destination ->
                                    NavigationBarItem(
                                        selected = selected == destination,
                                        onClick = { selected = destination },
                                        icon = {
                                            if (destination == PortalDestination.Messages &&
                                                conversations.any { it.unreadCount > 0 }
                                            ) {
                                                BadgedBox(badge = { Badge() }) {
                                                    Icon(destination.icon, stringResource(destination.label))
                                                }
                                            } else {
                                                Icon(destination.icon, stringResource(destination.label))
                                            }
                                        },
                                        label = { Text(stringResource(destination.label)) },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            PortalBackground(Modifier.padding(padding)) {
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
                            header = {
                                PortalBrandMark(44.dp)
                                Spacer(Modifier.height(18.dp))
                            },
                        ) {
                            primaryDestinations.forEach { destination ->
                                NavigationRailItem(
                                    selected = selected == destination,
                                    onClick = { selected = destination },
                                    icon = { Icon(destination.icon, stringResource(destination.label)) },
                                    label = { Text(stringResource(destination.label)) },
                                )
                            }
                        }
                        VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
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
                                    transitionSpec = {
                                        (fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it / 14 }) togetherWith
                                            (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 18 })
                                    },
                                    label = "portal-destination",
                                ) { destination ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                                                expanded,
                                                Modifier.fillMaxSize().widthIn(max = 1180.dp),
                                            )
                                            PortalDestination.Courses -> PortalCourseList(
                                                courses,
                                                onCourse,
                                                expanded,
                                                Modifier.fillMaxSize().widthIn(max = 1180.dp),
                                            )
                                            PortalDestination.Messages -> MessageListScreen(
                                                account,
                                                conversations,
                                                online,
                                                onConversation,
                                                onNewMessage,
                                                { viewModel.refreshConversations(account.id) },
                                                Modifier.fillMaxSize().widthIn(max = 960.dp),
                                            )
                                            PortalDestination.Calendar -> PortalEventList(
                                                events,
                                                Modifier.fillMaxSize().widthIn(max = 900.dp),
                                            )
                                            PortalDestination.Notifications -> PortalNotificationList(
                                                notifications,
                                                { viewModel.markNotificationRead(account.id, it) },
                                                Modifier.fillMaxSize().widthIn(max = 900.dp),
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
                                                Modifier.fillMaxSize().widthIn(max = 760.dp),
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
    expanded: Boolean,
    modifier: Modifier,
) {
    val now = System.currentTimeMillis() / 1_000L
    val summary = buildPortalDashboardSnapshot(courses, events, conversations, now)
    val upcoming = events.filter { it.startEpochSeconds >= now }.sortedBy { it.startEpochSeconds }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            PortalHomeHero(account)
        }
        item {
            summary.nextEvent?.let { PortalNextActionCard(it) }
                ?: PortalNoUpcomingCard()
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OverviewTile(
                    summary.unreadMessageCount.toString(),
                    stringResource(R.string.unread_messages),
                    Icons.Outlined.AddComment,
                    Modifier.weight(1f).clickable(onClick = onMessages),
                )
                OverviewTile(
                    summary.upcomingEventCount.toString(),
                    stringResource(R.string.upcoming_schedule),
                    Icons.Outlined.CalendarMonth,
                    Modifier.weight(1f),
                )
                OverviewTile(
                    summary.activeCourseCount.toString(),
                    stringResource(R.string.active_courses),
                    Icons.AutoMirrored.Outlined.MenuBook,
                    Modifier.weight(1f),
                )
            }
        }
        if (courses.isNotEmpty()) {
            item {
                PortalSectionHeader(
                    stringResource(R.string.recent_courses),
                    supportingText = stringResource(R.string.course_section_supporting),
                    trailing = {
                        PortalStatusPill(courses.size.toString(), icon = Icons.Outlined.School)
                    },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(courses.take(if (expanded) 6 else 4), key = { "home-course:${it.id}" }) { course ->
                        PortalHomeCourseCard(course, onCourse)
                    }
                }
            }
        } else {
            item { PortalEmptyState(Icons.Outlined.School, stringResource(R.string.empty_courses)) }
        }
        if (expanded) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PortalSectionHeader(stringResource(R.string.upcoming_schedule))
                        if (upcoming.isEmpty()) {
                            PortalEmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_events))
                        } else upcoming.take(3).forEach { PortalEventCard(it) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PortalSectionHeader(stringResource(R.string.grades))
                        if (grades.isEmpty()) {
                            PortalEmptyState(Icons.Outlined.CheckCircle, stringResource(R.string.empty_grades))
                        } else grades.take(3).forEach { PortalGradeCard(it) }
                    }
                }
            }
        } else {
            item { PortalSectionHeader(stringResource(R.string.upcoming_schedule)) }
            if (upcoming.isEmpty()) item { PortalEmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_events)) }
            items(upcoming.take(4), key = { "home-event:${it.id}" }) { PortalEventCard(it) }
            if (grades.isNotEmpty()) {
                item { PortalSectionHeader(stringResource(R.string.grades)) }
                items(grades.take(4), key = { "home-grade:${it.courseId}:${it.itemId}" }) { PortalGradeCard(it) }
            }
        }
        if (grades.isNotEmpty()) {
            item { Spacer(Modifier.height(2.dp)) }
        }
        if (notifications.any { !it.read }) {
            item {
                PortalStatusPill(
                    pluralStringResource(
                        R.plurals.unread_updates_count,
                        notifications.count { !it.read },
                        notifications.count { !it.read },
                    ),
                    icon = Icons.Outlined.Notifications,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun PortalHomeHero(account: SiteAccount) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        shadowElevation = 6.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(PortalNavy, PortalTealDark, Color(0xFF08766B))),
            ),
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).size(142.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.055f)),
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.welcome_back),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9EE3D6),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    account.fullName ?: account.username ?: account.siteName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    account.siteName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                PortalStatusPill(
                    account.lastSyncEpochSeconds?.let { stringResource(R.string.last_sync, portalDate(it)) }
                        ?: stringResource(R.string.never_synced),
                    icon = Icons.Outlined.CloudDone,
                )
            }
        }
    }
}

@Composable
private fun PortalNextActionCard(event: MoodleCalendarEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(Icons.Outlined.Schedule, null, Modifier.padding(12.dp).size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PortalEyebrow(stringResource(R.string.next_activity))
                Text(event.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    portalDate(event.startEpochSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PortalNoUpcomingCard() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.nothing_due), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.nothing_due_supporting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewTile(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun PortalHomeCourseCard(course: MoodleCourse, onCourse: (Long) -> Unit) {
    Card(
        onClick = { onCourse(course.id) },
        modifier = Modifier.width(246.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        PortalCourseCover(course.id, course.fullName, Modifier.fillMaxWidth().height(126.dp))
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(course.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                course.shortName.ifBlank { stringResource(R.string.course_label) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PortalCourseList(
    courses: List<MoodleCourse>,
    onCourse: (Long) -> Unit,
    expanded: Boolean,
    modifier: Modifier,
) {
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    val now = System.currentTimeMillis() / 1_000L
    val filtered = courses.filter { course ->
        (!activeOnly || course.endDate == null || course.endDate >= now) &&
            (query.isBlank() || course.fullName.contains(query, true) || course.shortName.contains(query, true))
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PortalEyebrow(stringResource(R.string.learning_space))
                Text(stringResource(R.string.courses), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.courses_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        query,
                        { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_courses)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (query.isNotBlank()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } }
                        } else null,
                        shape = CircleShape,
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(activeOnly, { activeOnly = true }, { Text(stringResource(R.string.active_courses)) })
                        FilterChip(!activeOnly, { activeOnly = false }, { Text(stringResource(R.string.all_courses)) })
                        PortalStatusPill(
                            pluralStringResource(R.plurals.course_count, filtered.size, filtered.size),
                            icon = Icons.Outlined.School,
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item { PortalEmptyState(Icons.Outlined.School, stringResource(R.string.empty_courses)) }
        } else if (expanded) {
            items(filtered.chunked(2), key = { row -> row.joinToString(":") { it.id.toString() } }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { course ->
                        PortalCourseCard(course, onCourse, Modifier.weight(1f), vertical = true)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            items(filtered, key = { it.id }) { PortalCourseCard(it, onCourse) }
        }
    }
}

@Composable
private fun PortalCourseCard(
    course: MoodleCourse,
    onCourse: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    Card(
        onClick = { onCourse(course.id) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
    ) {
        if (vertical) {
            PortalCourseCover(course.id, course.fullName, Modifier.fillMaxWidth().height(132.dp))
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (!vertical) {
                PortalCourseCover(
                    course.id,
                    course.fullName,
                    Modifier.width(94.dp).fillMaxHeight(),
                    compact = true,
                )
            }
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(course.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (course.shortName.isNotBlank() && course.shortName != course.fullName) {
                    PortalStatusPill(course.shortName, emphasized = true)
                }
                if (course.summaryHtml.isNotBlank()) {
                    Text(
                        portalPlainText(course.summaryHtml),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                course.endDate?.let {
                    Text(
                        stringResource(R.string.course_ends, portalDate(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalEventList(events: List<MoodleCalendarEvent>, modifier: Modifier) {
    val upcoming = events.sortedBy { it.startEpochSeconds }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PortalEyebrow(stringResource(R.string.timeline))
                Text(stringResource(R.string.calendar), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.calendar_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (upcoming.isEmpty()) item { PortalEmptyState(Icons.Outlined.CalendarMonth, stringResource(R.string.empty_events)) }
        items(upcoming, key = { it.id }) { PortalEventCard(it) }
    }
}

@Composable
private fun PortalEventCard(event: MoodleCalendarEvent) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                modifier = Modifier.width(58.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(portalMonth(event.startEpochSeconds), style = MaterialTheme.typography.labelSmall)
                    Text(portalDay(event.startEpochSeconds), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    portalTime(event.startEpochSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (event.descriptionHtml.isNotBlank()) {
                    Text(
                        portalPlainText(event.descriptionHtml),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalGradeCard(grade: MoodleGrade) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(9.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(grade.itemName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(grade.rangeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                grade.gradeFormatted.ifBlank { grade.percentageFormatted },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PortalNotificationList(
    notifications: List<MoodleNotification>,
    onOpen: (MoodleNotification) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PortalEyebrow(stringResource(R.string.inbox))
                Text(stringResource(R.string.notifications), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.notifications_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (notifications.isEmpty()) item { PortalEmptyState(Icons.Outlined.Notifications, stringResource(R.string.empty_notifications)) }
        items(notifications, key = { it.id }) { notification ->
            Surface(
                Modifier.fillMaxWidth().clickable { onOpen(notification) },
                color = if (notification.read) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape)
                            .background(if (notification.read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            notification.subject,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (notification.read) FontWeight.Medium else FontWeight.Bold,
                        )
                        Text(
                            portalPlainText(notification.fullMessageHtml),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(portalDate(notification.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PortalEyebrow(stringResource(R.string.account_and_privacy))
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialAvatar(account.fullName ?: account.username ?: account.siteName, 52.dp)
                        Column(Modifier.weight(1f)) {
                            Text(account.fullName ?: account.username.orEmpty(), style = MaterialTheme.typography.titleLarge)
                            Text(account.siteName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Text(account.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PortalStatusPill(
                        if (account.connectionMode == ConnectionMode.NativeApi) "Native API" else stringResource(R.string.html_mode),
                        icon = Icons.Outlined.Security,
                        emphasized = true,
                    )
                    account.moodleVersion?.let { Text(stringResource(R.string.moodle_version, it)) }
                }
            }
        }
        item {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.message_preview_setting), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.message_preview_setting_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
internal fun PortalReauthenticationScreen(account: SiteAccount, viewModel: AppViewModel, modifier: Modifier) {
    var username by remember(account.id) { mutableStateOf(account.username.orEmpty()) }
    var password by remember(account.id) { mutableStateOf("") }
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
                    Icon(Icons.Outlined.AccountCircle, null, Modifier.padding(12.dp).size(30.dp), tint = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.session_expired), style = MaterialTheme.typography.headlineSmall)
                Text(account.siteName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.session_expired_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.sign_in)) }
            }
        }
    }
}

@Composable
private fun OfflineBanner(lastSync: Long?) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                lastSync?.let { stringResource(R.string.offline_cached_at, portalDate(it)) }
                    ?: stringResource(R.string.offline_no_cache),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: "M"
    val color = portalCoursePalette(name.hashCode().toLong(), false).end
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = color,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initial, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PortalEmptyState(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(13.dp).size(28.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PortalLoadingSkeleton() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            Modifier.fillMaxWidth().height(166.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {}
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Surface(
                    Modifier.weight(1f).height(96.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {}
            }
        }
        repeat(3) {
            Surface(
                Modifier.fillMaxWidth().height(94.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
    }
}

private fun portalPlainText(html: String): String = org.jsoup.Jsoup.parseBodyFragment(html).text().trim()

private fun portalDate(epochSeconds: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(epochSeconds * 1_000L))

private fun portalMonth(epochSeconds: Long): String = SimpleDateFormat("MMM", Locale.getDefault())
    .format(Date(epochSeconds * 1_000L)).uppercase()

private fun portalDay(epochSeconds: Long): String = SimpleDateFormat("d", Locale.getDefault())
    .format(Date(epochSeconds * 1_000L))

private fun portalTime(epochSeconds: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT)
    .format(Date(epochSeconds * 1_000L))
