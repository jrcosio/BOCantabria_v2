package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem

interface ContentRepository {

    /**
     * Returns the content items.
     *
     * Never throws: failures come back as [AppResult.Failure]. An empty list is a success, not
     * a failure. Idempotent: calling it twice has no observable side effects.
     */
    suspend fun getContentItems(): AppResult<List<ContentItem>>
}
