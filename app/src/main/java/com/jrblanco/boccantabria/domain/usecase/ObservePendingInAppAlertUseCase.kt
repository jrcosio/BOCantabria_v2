package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.repository.InAppAlertStore
import kotlinx.coroutines.flow.Flow

/** The message waiting to be shown inside the application, if any. */
class ObservePendingInAppAlertUseCase(private val store: InAppAlertStore) {

    operator fun invoke(): Flow<InAppAlert?> = store.observePending()
}
