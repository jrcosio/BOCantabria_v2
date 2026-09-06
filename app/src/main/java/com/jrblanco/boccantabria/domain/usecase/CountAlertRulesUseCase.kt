package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.repository.AlertRepository

/** How many rules exist. The form asks before saving to know whether this is the first one. */
class CountAlertRulesUseCase(private val repository: AlertRepository) {

    suspend operator fun invoke(): Int = repository.countRules()
}
