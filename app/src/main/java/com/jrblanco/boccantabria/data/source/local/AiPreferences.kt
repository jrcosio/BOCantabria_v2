package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Whether this installation has already been told that the document text leaves the device.
 *
 * One boolean per installation. Room would cost a table, a data-access object and a migration to
 * store a single bit, and a key-value table is the doorway to a junk drawer; `DataStore` would be a
 * new dependency for the same bit. `SharedPreferences` is on the platform and is enough
 * (research.md D-023).
 */
interface AiPreferences {

    fun observeNoticeAccepted(): Flow<Boolean>

    suspend fun acceptNotice()
}

class SharedPreferencesAiPreferences(
    private val preferences: SharedPreferences,
    private val dispatchers: DispatcherProvider,
) : AiPreferences {

    /**
     * A listener rather than a single read, so that accepting the notice on one screen is seen by
     * whatever else is observing without anyone having to remember to refresh it.
     */
    override fun observeNoticeAccepted(): Flow<Boolean> = callbackFlow {
        trySend(preferences.getBoolean(KEY_NOTICE_ACCEPTED, false))
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NOTICE_ACCEPTED || key == null) {
                trySend(preferences.getBoolean(KEY_NOTICE_ACCEPTED, false))
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.flowOn(dispatchers.io)

    override suspend fun acceptNotice() = withContext(dispatchers.io) {
        preferences.edit { putBoolean(KEY_NOTICE_ACCEPTED, true) }
    }

    private companion object {
        /**
         * Versioned, and it must be versioned again whenever the wording of the notice changes
         * substantially.
         *
         * Feature 009 added a sentence — the service may use the text of that public document to
         * improve its models — and whoever had already accepted the previous notice never read it.
         *
         * Feature 010 changed something bigger than a sentence: what leaves the device is no longer
         * the **text** we extracted but the **document itself**, and the service keeps it for a
         * limited time. Accepting «the text is sent» was not accepting that, so the key goes up again
         * (010 FR-032, FR-033).
         *
         * FR-045 of feature 007 still holds: shown at most once per device **and per version of the
         * notice**. Old keys are left where they are; clearing them would need a preference migration
         * to gain nothing, and reading one would be taking as accepted a text nobody has seen
         * (009 research.md D-113, FR-031a).
         */
        const val KEY_NOTICE_ACCEPTED = "ai_notice_accepted_v3"
    }
}

/**
 * Built here and not in the Koin module for the same reason as the database and the HTTP client:
 * that package must not name a platform detail.
 */
fun aiPreferences(context: Context, dispatchers: DispatcherProvider): AiPreferences =
    SharedPreferencesAiPreferences(
        preferences = context.getSharedPreferences("boc_ai", Context.MODE_PRIVATE),
        dispatchers = dispatchers,
    )
