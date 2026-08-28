package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.repository.ContentRepository

/**
 * Adds no logic today. It exists so the presentation layer never knows about repositories, and
 * so there is one obvious place to put the logic the moment it appears.
 */
class GetContentItemsUseCase(
    private val repository: ContentRepository,
) {
    suspend operator fun invoke(): AppResult<List<ContentItem>> = repository.getContentItems()
}
