package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.BulletinHeaderData
import com.jrblanco.boccantabria.domain.model.HomeSelection
import com.jrblanco.boccantabria.domain.repository.PublicationRepository
import kotlinx.coroutines.flow.Flow

/** What the editorial header shows: the date of the selection and how much it holds. */
class ObserveBulletinHeaderUseCase(
    private val repository: PublicationRepository,
) {
    operator fun invoke(selection: HomeSelection): Flow<BulletinHeaderData> =
        repository.observeHeader(selection)
}
