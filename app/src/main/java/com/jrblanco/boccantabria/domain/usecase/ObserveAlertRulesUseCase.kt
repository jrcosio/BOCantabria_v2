package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.LocalDay
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.domain.model.AlertRuleOverview
import com.jrblanco.boccantabria.domain.repository.AlertRepository
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * The rules with what their cards say, counted in the person's local day.
 *
 * The zone is injectable so a test can pin midnight; in production it is the phone's
 * (research.md D-432).
 */
class ObserveAlertRulesUseCase(
    private val repository: AlertRepository,
    private val time: TimeProvider,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    operator fun invoke(): Flow<List<AlertRuleOverview>> =
        repository.observeRules(dayStart = LocalDay.startOf(time.nowMillis(), zone))
}
