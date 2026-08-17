package org.moodle.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.moodle.core.model.AssignmentSubmissionStatus
import org.moodle.core.model.ConnectionMode
import org.moodle.core.model.MoodleAssignment
import org.moodle.core.model.MoodleCalendarEvent
import org.moodle.core.model.MoodleCourse
import org.moodle.core.model.MoodleFile
import org.moodle.core.model.MoodleGrade
import org.moodle.core.model.MessageDraft
import org.moodle.core.model.MessageSendState
import org.moodle.core.model.MoodleConversation
import org.moodle.core.model.MoodleMessage
import org.moodle.core.model.MoodleMessageUser
import org.moodle.core.model.MoodleNotification
import org.moodle.core.model.MoodleModule
import org.moodle.core.model.MoodleModuleContent
import org.moodle.core.model.MoodlePublicConfig
import org.moodle.core.model.MoodleResult
import org.moodle.core.model.MoodleSection
import org.moodle.core.model.SiteAccount
import org.moodle.data.repository.MoodleAuthRepository
import org.moodle.data.repository.MoodleRepository
import org.moodle.data.repository.conversationDraftKey
import org.moodle.data.repository.userDraftKey
import org.moodle.data.local.AppPreferences
import java.io.File
import javax.inject.Inject

data class AppUiState(
    val busy: Boolean = false,
    val inspectedSite: MoodlePublicConfig? = null,
    val error: String? = null,
    val assignments: List<MoodleAssignment> = emptyList(),
    val submissionStatus: AssignmentSubmissionStatus? = null,
    val moduleContent: MoodleModuleContent? = null,
    val moduleContentModuleId: Long? = null,
    val messageSendState: MessageSendState = MessageSendState.Idle,
    val messageSearchResults: List<MoodleMessageUser> = emptyList(),
    val messageSearchBusy: Boolean = false,
)

sealed interface AppEvent {
    data class OpenAccount(val account: SiteAccount) : AppEvent
    data class OpenExternal(val url: String) : AppEvent
    data class OpenFile(val file: File, val mimeType: String?) : AppEvent
    data class OpenConversation(val accountId: String, val conversationId: Long) : AppEvent
    data object ReturnToAccounts : AppEvent
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: MoodleAuthRepository,
    private val moodleRepository: MoodleRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _events = Channel<AppEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val accounts = authRepository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeAccount = authRepository.activeAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val showMessagePreview = appPreferences.showMessagePreview
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private var searchJob: Job? = null

    fun inspectSite(url: String) = launchBusy {
        when (val result = authRepository.inspectSite(url)) {
            is MoodleResult.Success -> _uiState.value = _uiState.value.copy(inspectedSite = result.value)
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun clearInspectedSite() {
        _uiState.value = _uiState.value.copy(inspectedSite = null, error = null)
    }

    fun login(username: String, password: String) = launchBusy {
        val config = _uiState.value.inspectedSite ?: return@launchBusy
        when (val result = authRepository.login(config, username, password)) {
            is MoodleResult.Success -> _events.send(AppEvent.OpenAccount(result.value))
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun reauthenticate(accountId: String, username: String, password: String) = launchBusy {
        when (val result = authRepository.reauthenticate(accountId, username, password)) {
            is MoodleResult.Success -> _events.send(AppEvent.OpenAccount(result.value))
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun beginSso() {
        val config = _uiState.value.inspectedSite ?: return
        when (val result = authRepository.beginSso(config)) {
            is MoodleResult.Success -> _events.trySend(AppEvent.OpenExternal(result.value))
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun handleSso(callback: Uri) = launchBusy {
        when (val result = authRepository.completeSso(callback)) {
            is MoodleResult.Success -> _events.send(AppEvent.OpenAccount(result.value))
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun openAccount(account: SiteAccount) = viewModelScope.launch {
        authRepository.activate(account.id)
        _events.send(AppEvent.OpenAccount(account.copy(isActive = true)))
    }

    fun handleMessageIntent(accountId: String?, conversationId: Long?) = viewModelScope.launch {
        if (accountId.isNullOrBlank() || conversationId == null || conversationId <= 0) return@launch
        authRepository.activate(accountId)
        _events.send(AppEvent.OpenConversation(accountId, conversationId))
    }

    fun removeAccount(accountId: String) = viewModelScope.launch {
        authRepository.remove(accountId)
        _events.send(AppEvent.ReturnToAccounts)
    }

    fun sync(accountId: String) = launchBusy {
        when (val result = moodleRepository.sync(accountId)) {
            is MoodleResult.Success -> moodleRepository.syncMessages(accountId, allowNotifications = false)
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun refreshCourse(accountId: String, courseId: Long) = launchBusy {
        when (val result = moodleRepository.refreshCourse(accountId, courseId)) {
            is MoodleResult.Success -> Unit
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun loadAssignments(accountId: String, courseId: Long, selectedId: Long? = null) = launchBusy {
        when (val result = moodleRepository.assignments(accountId, courseId)) {
            is MoodleResult.Success -> {
                _uiState.value = _uiState.value.copy(assignments = result.value, submissionStatus = null)
                val assignment = result.value.firstOrNull { it.id == selectedId }
                if (assignment != null) loadSubmissionStatus(accountId, assignment.id)
            }
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun loadSubmissionStatus(accountId: String, assignmentId: Long) = viewModelScope.launch {
        when (val result = moodleRepository.submissionStatus(accountId, assignmentId)) {
            is MoodleResult.Success -> _uiState.value = _uiState.value.copy(submissionStatus = result.value)
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun loadModuleContent(accountId: String, module: MoodleModule) = launchBusy {
        _uiState.value = _uiState.value.copy(moduleContent = null, moduleContentModuleId = module.id)
        when (val result = moodleRepository.moduleContent(accountId, module)) {
            is MoodleResult.Success -> _uiState.value = _uiState.value.copy(moduleContent = result.value)
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun submitAssignment(
        accountId: String,
        assignment: MoodleAssignment,
        text: String,
        fileUri: Uri?,
    ) = launchBusy {
        when (val result = moodleRepository.submitAssignment(accountId, assignment, text, fileUri)) {
            is MoodleResult.Success -> loadSubmissionStatus(accountId, assignment.id)
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun markNotificationRead(accountId: String, notification: MoodleNotification) = viewModelScope.launch {
        moodleRepository.markNotificationRead(accountId, notification.id)
        notification.contextUrl?.let { openAuthenticatedUrl(accountId, it) }
    }

    fun openAuthenticatedUrl(accountId: String, url: String) = viewModelScope.launch {
        _events.send(AppEvent.OpenExternal(moodleRepository.authenticatedWebUrl(accountId, url)))
    }

    fun download(accountId: String, file: MoodleFile) = launchBusy {
        when (val result = moodleRepository.cacheFile(accountId, file)) {
            is MoodleResult.Success -> _events.send(AppEvent.OpenFile(result.value, file.mimeType))
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun courses(accountId: String): Flow<List<MoodleCourse>> = moodleRepository.courses(accountId)
    fun sections(accountId: String, courseId: Long): Flow<List<MoodleSection>> = moodleRepository.sections(accountId, courseId)
    fun grades(accountId: String): Flow<List<MoodleGrade>> = moodleRepository.grades(accountId)
    fun events(accountId: String): Flow<List<MoodleCalendarEvent>> = moodleRepository.events(accountId)
    fun notifications(accountId: String): Flow<List<MoodleNotification>> = moodleRepository.notifications(accountId)
    fun conversations(accountId: String): Flow<List<MoodleConversation>> = moodleRepository.conversations(accountId)
    fun messages(accountId: String, conversationId: Long): Flow<List<MoodleMessage>> =
        moodleRepository.messages(accountId, conversationId)
    fun messageDraft(accountId: String, draftKey: String): Flow<MessageDraft?> =
        moodleRepository.messageDraft(accountId, draftKey)

    fun refreshConversations(accountId: String, offset: Int = 0) = launchBusy {
        when (val result = moodleRepository.refreshConversations(accountId, offset)) {
            is MoodleResult.Success -> Unit
            is MoodleResult.Failure -> showError(result.error.message)
        }
    }

    fun refreshConversation(accountId: String, conversationId: Long, offset: Int = 0) = viewModelScope.launch {
        if (offset == 0) _uiState.value = _uiState.value.copy(busy = true, error = null)
        when (val result = moodleRepository.refreshMessages(accountId, conversationId, offset)) {
            is MoodleResult.Success -> {
                if (offset == 0) moodleRepository.markConversationRead(accountId, conversationId)
            }
            is MoodleResult.Failure -> showError(result.error.message)
        }
        if (offset == 0) _uiState.value = _uiState.value.copy(busy = false)
    }

    fun searchMessageUsers(accountId: String, query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(messageSearchResults = emptyList(), messageSearchBusy = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _uiState.value = _uiState.value.copy(messageSearchBusy = true, error = null)
            when (val result = moodleRepository.searchMessageUsers(accountId, query)) {
                is MoodleResult.Success -> _uiState.value = _uiState.value.copy(
                    messageSearchResults = result.value,
                    messageSearchBusy = false,
                )
                is MoodleResult.Failure -> {
                    _uiState.value = _uiState.value.copy(messageSearchBusy = false)
                    showError(result.error.message)
                }
            }
        }
    }

    fun sendMessage(accountId: String, conversationId: Long, body: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(messageSendState = MessageSendState.Sending, error = null)
        when (val result = moodleRepository.sendMessage(accountId, conversationId, body)) {
            is MoodleResult.Success -> _uiState.value = _uiState.value.copy(
                messageSendState = MessageSendState.Sent(conversationId),
            )
            is MoodleResult.Failure -> _uiState.value = _uiState.value.copy(
                messageSendState = MessageSendState.Failed(result.error.message),
            )
        }
    }

    fun startConversation(accountId: String, userId: Long, body: String) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(messageSendState = MessageSendState.Sending, error = null)
        when (val result = moodleRepository.startConversation(accountId, userId, body)) {
            is MoodleResult.Success -> {
                _uiState.value = _uiState.value.copy(messageSendState = MessageSendState.Sent(result.value))
                _events.send(AppEvent.OpenConversation(accountId, result.value))
            }
            is MoodleResult.Failure -> _uiState.value = _uiState.value.copy(
                messageSendState = MessageSendState.Failed(result.error.message),
            )
        }
    }

    fun saveConversationDraft(accountId: String, conversationId: Long, body: String) = viewModelScope.launch {
        moodleRepository.saveMessageDraft(accountId, conversationDraftKey(conversationId), body)
    }

    fun saveUserDraft(accountId: String, userId: Long, body: String) = viewModelScope.launch {
        moodleRepository.saveMessageDraft(accountId, userDraftKey(userId), body)
    }

    fun resetMessageComposer() {
        _uiState.value = _uiState.value.copy(messageSendState = MessageSendState.Idle)
    }

    fun setMessagePreview(enabled: Boolean) = viewModelScope.launch {
        appPreferences.setShowMessagePreview(enabled)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(busy = true, error = null)
        try {
            block()
        } finally {
            _uiState.value = _uiState.value.copy(busy = false)
        }
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}
