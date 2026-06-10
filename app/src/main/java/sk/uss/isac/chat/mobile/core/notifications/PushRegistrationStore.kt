package sk.uss.isac.chat.mobile.core.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PushRegistrationState(
    val currentToken: String? = null,
    val lastSyncedToken: String? = null,
    val lastSyncedSubject: String? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastSyncError: String? = null,
    val lastPushTestAtEpochMillis: Long? = null,
    val lastPushTestSummary: String? = null,
    val lastPushTestDelivered: Boolean? = null
)

interface PushRegistrationStore {
    suspend fun readState(): PushRegistrationState
    suspend fun saveCurrentToken(token: String)
    suspend fun markSynced(subject: String, token: String, syncedAtEpochMillis: Long)
    suspend fun markSyncFailed(error: String, attemptedAtEpochMillis: Long)
    suspend fun savePushTestResult(summary: String, delivered: Boolean, testedAtEpochMillis: Long)
    suspend fun clearSyncedMarker()
}

class SharedPreferencesPushRegistrationStore(
    context: Context
) : PushRegistrationStore {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences("push_registration", Context.MODE_PRIVATE)

    override suspend fun readState(): PushRegistrationState {
        return PushRegistrationState(
            currentToken = preferences.getString(Keys.CurrentToken, null)?.trim()?.ifBlank { null },
            lastSyncedToken = preferences.getString(Keys.LastSyncedToken, null)?.trim()?.ifBlank { null },
            lastSyncedSubject = preferences.getString(Keys.LastSyncedSubject, null)?.trim()?.ifBlank { null },
            lastSyncedAtEpochMillis = preferences.getLong(Keys.LastSyncedAtEpochMillis, -1L).takeIf { it > 0L },
            lastSyncError = preferences.getString(Keys.LastSyncError, null)?.trim()?.ifBlank { null },
            lastPushTestAtEpochMillis = preferences.getLong(Keys.LastPushTestAtEpochMillis, -1L).takeIf { it > 0L },
            lastPushTestSummary = preferences.getString(Keys.LastPushTestSummary, null)?.trim()?.ifBlank { null },
            lastPushTestDelivered = preferences.takeIf { it.contains(Keys.LastPushTestDelivered) }
                ?.getBoolean(Keys.LastPushTestDelivered, false)
        )
    }

    override suspend fun saveCurrentToken(token: String) {
        editPreferences { putString(Keys.CurrentToken, token.trim()) }
    }

    override suspend fun markSynced(subject: String, token: String, syncedAtEpochMillis: Long) {
        editPreferences {
            putString(Keys.LastSyncedSubject, subject.trim())
            putString(Keys.LastSyncedToken, token.trim())
            putLong(Keys.LastSyncedAtEpochMillis, syncedAtEpochMillis)
            remove(Keys.LastSyncError)
        }
    }

    override suspend fun markSyncFailed(error: String, attemptedAtEpochMillis: Long) {
        editPreferences {
            putLong(Keys.LastSyncedAtEpochMillis, attemptedAtEpochMillis)
            putString(Keys.LastSyncError, error.trim())
        }
    }

    override suspend fun savePushTestResult(summary: String, delivered: Boolean, testedAtEpochMillis: Long) {
        editPreferences {
            putLong(Keys.LastPushTestAtEpochMillis, testedAtEpochMillis)
            putString(Keys.LastPushTestSummary, summary.trim())
            putBoolean(Keys.LastPushTestDelivered, delivered)
        }
    }

    override suspend fun clearSyncedMarker() {
        editPreferences {
            remove(Keys.LastSyncedSubject)
            remove(Keys.LastSyncedToken)
            remove(Keys.LastSyncedAtEpochMillis)
            remove(Keys.LastSyncError)
        }
    }

    private suspend fun editPreferences(block: SharedPreferences.Editor.() -> Unit) {
        withContext(Dispatchers.IO) {
            preferences.edit().apply(block).commit()
        }
    }

    private object Keys {
        const val CurrentToken = "current_push_token"
        const val LastSyncedToken = "last_synced_push_token"
        const val LastSyncedSubject = "last_synced_subject"
        const val LastSyncedAtEpochMillis = "last_synced_at_epoch_millis"
        const val LastSyncError = "last_sync_error"
        const val LastPushTestAtEpochMillis = "last_push_test_at_epoch_millis"
        const val LastPushTestSummary = "last_push_test_summary"
        const val LastPushTestDelivered = "last_push_test_delivered"
    }
}
