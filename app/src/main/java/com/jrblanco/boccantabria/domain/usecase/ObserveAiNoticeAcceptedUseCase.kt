package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AiSummaryRepository
import kotlinx.coroutines.flow.Flow

/** Whether this installation has already been told that the document text leaves the device. */
class ObserveAiNoticeAcceptedUseCase(
    private val repository: AiSummaryRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeNoticeAccepted()
}
