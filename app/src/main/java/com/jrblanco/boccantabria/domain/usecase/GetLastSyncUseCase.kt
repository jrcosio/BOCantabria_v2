package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.PublicationRepository

/** When the bulletin was last checked successfully, or `null` if never. For the settings sheet. */
class GetLastSyncUseCase(private val repository: PublicationRepository) {

    suspend operator fun invoke(): Long? = repository.lastSuccessfulSyncAt()
}
