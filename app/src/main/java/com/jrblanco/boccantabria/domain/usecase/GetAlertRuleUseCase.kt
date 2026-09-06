package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.repository.AlertRepository

/** One rule by id, for the form. `null` when it no longer exists. */
class GetAlertRuleUseCase(private val repository: AlertRepository) {

    suspend operator fun invoke(id: String): AlertRule? = repository.rule(id)
}
