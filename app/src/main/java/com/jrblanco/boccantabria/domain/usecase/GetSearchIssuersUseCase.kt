package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow

/**
 * The organisations the filter can offer.
 *
 * Taken from what is stored, never from a fixed catalogue, so the sheet never offers an
 * organisation with not a single announcement behind it.
 */
class GetSearchIssuersUseCase(private val repository: SearchRepository) {

    operator fun invoke(): Flow<List<String>> = repository.observeIssuers()
}
