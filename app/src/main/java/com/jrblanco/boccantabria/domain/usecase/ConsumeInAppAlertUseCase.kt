package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.InAppAlertStore

/** The shell has shown —or deliberately not shown— the pending message. */
class ConsumeInAppAlertUseCase(private val store: InAppAlertStore) {

    operator fun invoke() = store.consume()
}
