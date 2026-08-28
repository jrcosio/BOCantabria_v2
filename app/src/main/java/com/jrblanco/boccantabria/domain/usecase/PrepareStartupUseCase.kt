package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.StartupStatus
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.domain.model.map

/**
 * Orchestrates the three startup checks.
 *
 * The policy lives here rather than in the view model so it stays plain Kotlin and can be checked
 * without standing up a presentation layer (research.md, D-006).
 */
class PrepareStartupUseCase(
    private val connectivity: ConnectivityRepository,
    private val appConfig: AppConfigRepository,
    private val appVersion: AppVersionProvider,
) {

    suspend operator fun invoke(): AppResult<StartupStatus> {
        // Offline wins over everything else: without a network there is no way to know either the
        // minimum supported version or whether the service is under maintenance, so asking would
        // only waste a request and blur which check failed.
        if (!connectivity.isOnline()) return AppResult.Failure(DomainError.Network)

        return appConfig.loadConfig().map { config ->
            when {
                appVersion.versionCode < config.minSupportedVersionCode -> StartupStatus.UpdateRequired
                config.maintenanceMessage != null -> StartupStatus.Maintenance(config.maintenanceMessage)
                else -> StartupStatus.Ready
            }
        }
    }
}
