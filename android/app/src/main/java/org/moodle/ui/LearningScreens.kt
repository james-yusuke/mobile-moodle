package org.moodle.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.moodle.R
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.NativeModuleType
import java.text.DateFormat
import java.util.Date

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
    LaunchedEffect(accountId, courseId) { viewModel.refreshCourse(accountId, courseId) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.course_contents)) },
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            sections.forEach { section ->
                item(key = "section:${section.id}") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            section.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (section.summaryHtml.isNotBlank()) Text(learningPlainText(section.summaryHtml))
                        HorizontalDivider(Modifier.padding(top = 4.dp))
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

@Composable
private fun ModuleCard(
    module: MoodleModule,
    onOpen: () -> Unit,
    onDownload: (org.moodle.core.model.MoodleFile) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    when (module.nativeType) {
                        NativeModuleType.Assignment -> Icons.AutoMirrored.Outlined.Assignment
                        NativeModuleType.Folder -> Icons.Outlined.Folder
                        NativeModuleType.Url, NativeModuleType.Unsupported -> Icons.Outlined.Link
                        else -> Icons.Outlined.Description
                    },
                    null,
                    Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(module.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    module.moduleType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (module.descriptionHtml.isNotBlank()) Text(learningPlainText(module.descriptionHtml), maxLines = 4)
                module.files.forEach { file ->
                    OutlinedButton(onClick = { onDownload(file) }) {
                        Text("${stringResource(R.string.download)}: ${file.name}")
                    }
                }
                if (module.nativeType == NativeModuleType.Assignment || module.webUrl != null) {
                    Button(onClick = onOpen) {
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
        topBar = {
            TopAppBar(
                title = { Text(content?.title ?: module?.name ?: stringResource(R.string.content)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (module == null || content == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (content.bodyHtml.isNotBlank()) item { Text(learningPlainText(content.bodyHtml)) }
                items(content.files, key = { it.url }) { file ->
                    OutlinedButton(onClick = { viewModel.download(accountId, file) }) {
                        Text("${stringResource(R.string.download)}: ${file.name}")
                    }
                }
                content.originalUrl?.let { originalUrl ->
                    item {
                        TextButton(onClick = { viewModel.openAuthenticatedUrl(accountId, originalUrl) }) {
                            Text(stringResource(R.string.open_original))
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
        topBar = {
            TopAppBar(
                title = { Text(assignment?.name ?: stringResource(R.string.assignment)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (assignment == null) {
            LoadingBox(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (assignment.introHtml.isNotBlank()) item { Text(learningPlainText(assignment.introHtml)) }
                assignment.dueDate?.let { item { Text(stringResource(R.string.due_date, learningDate(it))) } }
                state.submissionStatus?.let { status ->
                    item { Text(stringResource(R.string.submission_status, status.status), fontWeight = FontWeight.Bold) }
                    status.feedbackHtml?.let { item { Text(stringResource(R.string.feedback)); Text(learningPlainText(it)) } }
                }
                if (assignment.allowsOnlineText) item {
                    OutlinedTextField(
                        onlineText,
                        { onlineText = it },
                        label = { Text(stringResource(R.string.online_text)) },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (assignment.allowsFiles) item {
                    OutlinedButton(onClick = { picker.launch("*/*") }) { Text(stringResource(R.string.select_file)) }
                    selectedFile?.lastPathSegment?.let { Text(stringResource(R.string.selected_file, it)) }
                }
                if (assignment.allowsOnlineText || assignment.allowsFiles) item {
                    Button(onClick = { confirm = true }, enabled = !state.busy) { Text(stringResource(R.string.submit)) }
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
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

private fun learningPlainText(html: String): String = org.jsoup.Jsoup.parseBodyFragment(html).text().trim()

private fun learningDate(epochSeconds: Long): String = DateFormat.getDateTimeInstance(
    DateFormat.MEDIUM,
    DateFormat.SHORT,
).format(Date(epochSeconds * 1_000L))
