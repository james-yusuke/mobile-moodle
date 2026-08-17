package org.moodle.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.moodle.R
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.NativeModuleType
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseScreen(
    accountId: String,
    courseId: Long,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onAssignment: (Long) -> Unit,
    onModule: (Long) -> Unit,
) {
    val sections by remember(accountId, courseId) { viewModel.sections(accountId, courseId) }
        .collectAsStateWithLifecycle(emptyList())
    val courses by remember(accountId) { viewModel.courses(accountId) }.collectAsStateWithLifecycle(emptyList())
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val course = courses.firstOrNull { it.id == courseId }
    LaunchedEffect(accountId, courseId) { viewModel.refreshCourse(accountId, courseId) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        course?.shortName?.ifBlank { course.fullName } ?: stringResource(R.string.course_contents),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                if (sections.isEmpty() && state.busy) {
                    LearningLoadingState()
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().widthIn(max = 900.dp),
                        contentPadding = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            PortalCourseCover(
                                courseId,
                                course?.fullName ?: stringResource(R.string.course_contents),
                                Modifier.fillMaxWidth().height(166.dp),
                            )
                        }
                        item {
                            PortalSectionHeader(
                                stringResource(R.string.course_contents),
                                supportingText = pluralStringResource(
                                    R.plurals.sections_count,
                                    sections.size,
                                    sections.size,
                                ),
                            )
                        }
                        if (sections.isEmpty()) {
                            item { PortalEmptyState(Icons.Outlined.Description, stringResource(R.string.no_sections)) }
                        }
                        sections.forEachIndexed { index, section ->
                            item(key = "section:${section.id}") {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    color = Color.Transparent,
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                (index + 1).toString().padStart(2, '0'),
                                                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(section.name, style = MaterialTheme.typography.titleLarge)
                                            if (section.summaryHtml.isNotBlank()) {
                                                Text(
                                                    learningPlainText(section.summaryHtml),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(section.modules, key = { "module:${section.id}:${it.id}" }) { module ->
                                ModuleCard(
                                    module,
                                    onOpen = {
                                        when {
                                            module.nativeType == NativeModuleType.Assignment ->
                                                onAssignment(module.instanceId ?: module.id)
                                            module.nativeType in setOf(
                                                NativeModuleType.Page,
                                                NativeModuleType.Resource,
                                                NativeModuleType.Folder,
                                            ) -> onModule(module.id)
                                            else -> module.webUrl?.let { viewModel.openAuthenticatedUrl(accountId, it) }
                                        }
                                    },
                                    onDownload = { viewModel.download(accountId, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: MoodleModule,
    onOpen: () -> Unit,
    onDownload: (org.moodle.core.model.MoodleFile) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    when (module.nativeType) {
                        NativeModuleType.Assignment -> Icons.AutoMirrored.Outlined.Assignment
                        NativeModuleType.Folder -> Icons.Outlined.Folder
                        NativeModuleType.Url, NativeModuleType.Unsupported -> Icons.Outlined.Link
                        else -> Icons.Outlined.Description
                    },
                    null,
                    Modifier.padding(10.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(module.name, style = MaterialTheme.typography.titleMedium)
                PortalStatusPill(module.moduleType.uppercase(), emphasized = true)
                if (module.descriptionHtml.isNotBlank()) {
                    Text(
                        learningPlainText(module.descriptionHtml),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                module.files.forEach { file ->
                    Surface(
                        onClick = { onDownload(file) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(file.name, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (module.nativeType == NativeModuleType.Assignment || module.webUrl != null) {
                    Button(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (module.nativeType) {
                                NativeModuleType.Assignment -> stringResource(R.string.assignment)
                                NativeModuleType.Page, NativeModuleType.Resource, NativeModuleType.Folder ->
                                    stringResource(R.string.view_content)
                                else -> stringResource(R.string.open_in_moodle)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleContentScreen(
    accountId: String,
    courseId: Long,
    moduleId: Long,
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val sections by remember(accountId, courseId) { viewModel.sections(accountId, courseId) }
        .collectAsStateWithLifecycle(emptyList())
    val module = sections.flatMap { it.modules }.firstOrNull { it.id == moduleId }
    LaunchedEffect(accountId, courseId, moduleId, module) {
        if (module == null && sections.isEmpty()) viewModel.refreshCourse(accountId, courseId)
        if (module != null) viewModel.loadModuleContent(accountId, module)
    }
    val content = state.moduleContent.takeIf { state.moduleContentModuleId == moduleId }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(content?.title ?: module?.name ?: stringResource(R.string.content)) },
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
            if (module == null || content == null) {
                LearningLoadingState()
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        Modifier.fillMaxSize().widthIn(max = 820.dp),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                PortalEyebrow(module.moduleType)
                                Text(content.title, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        if (content.bodyHtml.isNotBlank()) {
                            item {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Text(
                                        learningPlainText(content.bodyHtml),
                                        Modifier.padding(20.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        if (content.files.isNotEmpty()) {
                            item { PortalSectionHeader(stringResource(R.string.downloads)) }
                        }
                        items(content.files, key = { it.url }) { file ->
                            Surface(
                                onClick = { viewModel.download(accountId, file) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(Icons.Outlined.CloudDownload, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                                        file.sizeBytes?.let {
                                            Text(
                                                formatFileSize(it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        content.originalUrl?.let { originalUrl ->
                            item {
                                TextButton(onClick = { viewModel.openAuthenticatedUrl(accountId, originalUrl) }) {
                                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.open_original))
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
internal fun AssignmentScreen(
    accountId: String,
    courseId: Long,
    assignmentId: Long,
    state: AppUiState,
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    var onlineText by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedFile = it }
    LaunchedEffect(accountId, courseId, assignmentId) {
        viewModel.loadAssignments(accountId, courseId, assignmentId)
    }
    val assignment = state.assignments.firstOrNull { it.id == assignmentId }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(assignment?.name ?: stringResource(R.string.assignment)) },
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
            if (assignment == null) {
                LearningLoadingState()
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        Modifier.fillMaxSize().widthIn(max = 820.dp),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PortalEyebrow(stringResource(R.string.assignment))
                                    Text(assignment.name, style = MaterialTheme.typography.headlineMedium)
                                    assignment.dueDate?.let {
                                        PortalStatusPill(
                                            stringResource(R.string.due_date, learningDate(it)),
                                            icon = Icons.Outlined.Schedule,
                                            emphasized = true,
                                        )
                                    }
                                }
                            }
                        }
                        if (assignment.introHtml.isNotBlank()) {
                            item {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Text(
                                        learningPlainText(assignment.introHtml),
                                        Modifier.padding(18.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                        state.submissionStatus?.let { status ->
                            item {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            stringResource(R.string.submission_status, status.status),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                                }
                            }
                            status.feedbackHtml?.let {
                                item {
                                    PortalSectionHeader(stringResource(R.string.feedback))
                                    Surface(
                                        Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    ) {
                                        Text(learningPlainText(it), Modifier.padding(18.dp))
                                    }
                                }
                            }
                        }
                        if (assignment.allowsOnlineText || assignment.allowsFiles) {
                            item { PortalSectionHeader(stringResource(R.string.prepare_submission)) }
                        }
                        if (assignment.allowsOnlineText) item {
                            OutlinedTextField(
                                onlineText,
                                { onlineText = it },
                                label = { Text(stringResource(R.string.online_text)) },
                                minLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        if (assignment.allowsFiles) item {
                            OutlinedButton(
                                onClick = { picker.launch("*/*") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                            ) {
                                Icon(Icons.Outlined.UploadFile, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.select_file))
                            }
                            selectedFile?.lastPathSegment?.let {
                                PortalStatusPill(stringResource(R.string.selected_file, it), icon = Icons.Outlined.CheckCircle)
                            }
                        }
                        if (assignment.allowsOnlineText || assignment.allowsFiles) item {
                            Button(
                                onClick = { confirm = true },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) { Text(stringResource(R.string.submit)) }
                        }
                    }
                }
            }
        }
    }
    if (confirm && assignment != null) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(stringResource(R.string.submit_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                viewModel.submitAssignment(accountId, assignment, onlineText, selectedFile)
            }) { Text(stringResource(R.string.submit)) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LearningLoadingState() }
}

@Composable
private fun LearningLoadingState() {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            Modifier.fillMaxWidth().height(154.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {}
        repeat(4) {
            Surface(
                Modifier.fillMaxWidth().height(88.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {}
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
}

private fun learningPlainText(html: String): String = org.jsoup.Jsoup.parseBodyFragment(html).text().trim()

private fun learningDate(epochSeconds: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(epochSeconds * 1_000L))
