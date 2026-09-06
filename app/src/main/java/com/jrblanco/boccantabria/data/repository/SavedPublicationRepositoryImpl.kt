package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.telemetry.AnalyticsEvent
import com.jrblanco.boccantabria.core.telemetry.AnalyticsTracker
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.SavedPublicationDao
import com.jrblanco.boccantabria.data.source.local.toDomain
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The saved mark, and the only thing that writes it.
 *
 * A repository of its own rather than three more methods on [PublicationRepositoryImpl], which
 * already takes ten dependencies: this answers to the person, that one to the source. It is also
 * the single place that reports the mark to analytics, so the three screens that offer the action
 * are covered from one spot instead of three that have to be kept in step.
 */
class SavedPublicationRepositoryImpl(
    private val savedPublicationDao: SavedPublicationDao,
    private val time: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : SavedPublicationRepository {

    // A local read failure must not kill the flow: the screen would be left with no state at all,
    // which reads as a frozen application rather than as an empty one. Until feature 014 a `catch`
    // emitted the empty list and the flow was over: Guardados showed «nada guardado» until the screen
    // was recreated (STAB-004). `recoverReads` retries and keeps observing; always the last operator.
    override fun observeSaved(): Flow<List<Publication>> =
        savedPublicationDao.observeSaved()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.io)
            .recoverReads(fallback = emptyList(), name = "saved", crashReporter = crashReporter)

    /** Three screens draw their bookmarks from this one flow: one dead subscription blanked them all. */
    override fun observeSavedKeys(): Flow<Set<String>> =
        savedPublicationDao.observeSavedKeys()
            .map { keys -> keys.toSet() }
            .flowOn(dispatchers.io)
            .recoverReads(fallback = emptySet(), name = "saved-keys", crashReporter = crashReporter)

    /**
     * Writes the instant when saving and clears it when unsaving.
     *
     * The instant comes from [time] and never from the system clock at the point of use: it is what
     * makes the order of the list verifiable in microseconds instead of by waiting.
     *
     * A key that is not stored touches no rows, and that is a success: there is nothing to do and
     * nothing has gone wrong.
     */
    override suspend fun setSaved(externalKey: String, saved: Boolean): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                savedPublicationDao.setSavedAt(
                    externalKey = externalKey,
                    savedAt = if (saved) time.nowMillis() else null,
                )
                analytics.track(
                    AnalyticsEvent(name = EVENT_SAVE, parameters = mapOf(PARAM_SAVED to saved.toString())),
                )
                AppResult.Success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unexpected: Throwable) {
                crashReporter.recordNonFatal(unexpected)
                // `DomainError` does not grow: there is nothing the screen could do differently
                // depending on why the store refused the write.
                AppResult.Failure(DomainError.Unknown)
            }
        }

    companion object {
        /** Counts and a flag. Never which publication: that is a personal-interest signal. */
        const val EVENT_SAVE: String = "publication_save"
        const val PARAM_SAVED: String = "saved"
    }
}
