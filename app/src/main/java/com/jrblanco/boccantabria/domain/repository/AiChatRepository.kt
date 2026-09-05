package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AiConversation
import com.jrblanco.boccantabria.domain.model.Publication
import kotlinx.coroutines.flow.Flow

/**
 * The conversation about a publication's official document.
 *
 * **Only [observeConversation] and [observeAvailability] return anything, and none of the five
 * suspend.** That is a decision and not an oversight: the work lives on a scope of the repository's
 * own, so leaving the screen does not cancel it. Cancelling would not refund the allowance — a request
 * is counted when it goes out — so an abandoned question would cost exactly the same and lose the
 * answer as well. Letting it run means whoever comes back finds it done
 * (011 research.md D-313).
 *
 * [discard] is called from the detail screen's `onCleared()`, where the caller's scope is already
 * dead, which is the second reason none of this suspends.
 */
interface AiChatRepository {

    /**
     * What has been said about [externalKey], and what is happening right now.
     *
     * **Observing never generates anything.** It is the first rule of the AI summary and it holds
     * here: opening the screen spends no allowance.
     *
     * Another key emits an empty conversation, because at most one is alive at a time. That is what
     * makes FR-011 structural rather than a promise.
     */
    fun observeConversation(externalKey: String): Flow<AiConversation>

    /**
     * Whether the service can be asked at all.
     *
     * The summary has no seam like this and only finds out when the button is pressed; FR-036 asks for
     * the screen to know **when it opens**, so that it can say so and disable sending instead of
     * spending a request to discover it (D-320b). Emits `false` with no credential, and makes no
     * request to decide.
     */
    fun observeAvailability(): Flow<Boolean>

    /** Does nothing if [question] is blank or another question is already in flight (FR-005, FR-006). */
    fun ask(publication: Publication, question: String)

    /** Resends the question the current failure points at, without making anyone retype it (FR-033). */
    fun retry(publication: Publication)

    /** Cancels whatever is in flight and empties. Called when the publication is left. */
    fun discard(externalKey: String)
}
