package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.BocSection
import com.jrblanco.boccantabria.domain.repository.BocSectionRepository

/** The official section tree, in the order the drawer presents it. */
class GetBocSectionsUseCase(
    private val repository: BocSectionRepository,
) {
    operator fun invoke(): List<BocSection> = repository.sections()
}
