package org.moodle.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.moodlePreferences by preferencesDataStore(name = "app_settings")

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val activeAccountId: Flow<String?> = context.moodlePreferences.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences[ACTIVE_ACCOUNT_ID] }

    val showMessagePreview: Flow<Boolean> = context.moodlePreferences.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences[SHOW_MESSAGE_PREVIEW] ?: false }

    suspend fun setActiveAccountId(accountId: String) {
        context.moodlePreferences.edit { it[ACTIVE_ACCOUNT_ID] = accountId }
    }

    suspend fun clearActiveAccountIfMatches(accountId: String) {
        context.moodlePreferences.edit { preferences ->
            if (preferences[ACTIVE_ACCOUNT_ID] == accountId) preferences.remove(ACTIVE_ACCOUNT_ID)
        }
    }

    suspend fun setShowMessagePreview(enabled: Boolean) {
        context.moodlePreferences.edit { it[SHOW_MESSAGE_PREVIEW] = enabled }
    }

    private companion object {
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        val SHOW_MESSAGE_PREVIEW = booleanPreferencesKey("show_message_preview")
    }
}
