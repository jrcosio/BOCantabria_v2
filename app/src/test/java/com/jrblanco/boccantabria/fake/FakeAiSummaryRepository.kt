package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AiSummary
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.domain.model.AiSummaryStatus
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** A summary store the test drives. */
class FakeAiSummaryRepository(
    initial: AiSummaryStatus = AiSummaryStatus.Idle,
    noticeAccepted: Boolean = true,
) : AiSummaryRepository {

    private val status = MutableStateFlow(initial)
    private val notice = MutableStateFlow(noticeAccepted)

    var result: AppResult<AiSummary> = AppResult.Success(aiSummary())

    /**
     * Held open, the generation stays in flight. That is what makes a transient state genuinely
     * observable: without something suspending, a StateFlow conflates it away before any collector
     * runs, and the test would be asserting on a race.
     */
    var gate: CompletableDeferred<Unit>? = null

    var calls: Int = 0
        private set
    var forcedCalls: Int = 0
        private set
    var accepted: Int = 0
        private set

    fun emit(next: AiSummaryStatus) {
        status.value = next
    }

    fun failWith(error: AiSummaryError) {
        result = AppResult.Failure(DomainError.Unknown)
        status.value = AiSummaryStatus.Failed(error)
    }

    override fun observeSummary(externalKey: String): Flow<AiSummaryStatus> = status

    override suspend fun generate(publication: Publication, force: Boolean): AppResult<AiSummary> {
        calls++
        if (force) forcedCalls++
        gate?.await()
        return result
    }

    override fun observeNoticeAccepted(): Flow<Boolean> = notice

    /** Every key this repository was asked to let go of, in order. */
    val releasedKeys: MutableList<String> = mutableListOf()

    override fun releaseDocumentSession(externalKey: String) {
        releasedKeys += externalKey
    }

    override suspend fun acceptNotice() {
        accepted++
        notice.value = true
    }
}
