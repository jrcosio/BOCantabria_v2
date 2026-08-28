package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigValues
import com.jrblanco.boccantabria.domain.model.AppConfig
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

class AppConfigRepositoryImpl(
    private val remoteConfigDataSource: RemoteConfigDataSource,
    private val dispatchers: DispatcherProvider,
) : AppConfigRepository {

    override suspend fun loadConfig(): AppResult<AppConfig> = withContext(dispatchers.io) {
        try {
            AppResult.Success(remoteConfigDataSource.fetchValues().toDomain())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            AppResult.Failure(DomainError.Network)
        }
    }
}

private fun RemoteConfigValues.toDomain() = AppConfig(
    minSupportedVersionCode = minSupportedVersionCode.toInt(),
    // An unset text arrives as an empty string. Normalising it to null here means nothing
    // downstream has to check for both null and blank.
    maintenanceMessage = maintenanceMessage.takeIf { it.isNotBlank() },
)
