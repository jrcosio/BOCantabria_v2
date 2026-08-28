package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.domain.model.AppConfig
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.StartupStatus
import com.jrblanco.boccantabria.domain.repository.AppConfigRepository
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The startup policy lives here rather than in the view model precisely so it can be checked like
 * this: plain Kotlin, no emulator, no coroutine plumbing beyond `runTest`.
 */
class PrepareStartupUseCaseTest {

    @Test
    fun `online with a supported version reaches Ready`() = runTest {
        val result = useCase(online = true, config = AppConfig(minSupportedVersionCode = 1))()

        assertEquals(AppResult.Success(StartupStatus.Ready), result)
    }

    @Test
    fun `offline fails without even asking for the configuration`() = runTest {
        val repository = CountingConfigRepository(AppConfig())
        val result = PrepareStartupUseCase(
            connectivity = FixedConnectivity(isOnline = false),
            appConfig = repository,
            appVersion = FixedVersion(INSTALLED_VERSION),
        )()

        assertEquals(AppResult.Failure(DomainError.Network), result)
        // Asking anyway would waste a request that cannot succeed, and would blur which check
        // actually failed.
        assertEquals(0, repository.calls)
    }

    @Test
    fun `a configuration failure propagates unchanged`() = runTest {
        val expected = AppResult.Failure(DomainError.Network)
        val result = PrepareStartupUseCase(
            connectivity = FixedConnectivity(isOnline = true),
            appConfig = FailingConfigRepository(expected),
            appVersion = FixedVersion(INSTALLED_VERSION),
        )()

        assertEquals(expected, result)
    }

    @Test
    fun `a version below the minimum reaches UpdateRequired`() = runTest {
        val config = AppConfig(minSupportedVersionCode = INSTALLED_VERSION + 1)

        val result = useCase(online = true, config = config)()

        assertEquals(AppResult.Success(StartupStatus.UpdateRequired), result)
    }

    @Test
    fun `a version equal to the minimum is supported`() = runTest {
        val config = AppConfig(minSupportedVersionCode = INSTALLED_VERSION)

        val result = useCase(online = true, config = config)()

        assertEquals(AppResult.Success(StartupStatus.Ready), result)
    }

    @Test
    fun `a maintenance message reaches Maintenance`() = runTest {
        val config = AppConfig(minSupportedVersionCode = 1, maintenanceMessage = MESSAGE)

        val result = useCase(online = true, config = config)()

        assertEquals(AppResult.Success(StartupStatus.Maintenance(MESSAGE)), result)
    }

    @Test
    fun `an obsolete version wins over a maintenance message`() = runTest {
        // Telling someone about a temporary incident is pointless when they cannot use the app at
        // all: the blocking reason has to be the actionable one.
        val config = AppConfig(
            minSupportedVersionCode = INSTALLED_VERSION + 1,
            maintenanceMessage = MESSAGE,
        )

        val result = useCase(online = true, config = config)()

        assertEquals(AppResult.Success(StartupStatus.UpdateRequired), result)
    }

    private fun useCase(online: Boolean, config: AppConfig) = PrepareStartupUseCase(
        connectivity = FixedConnectivity(online),
        appConfig = CountingConfigRepository(config),
        appVersion = FixedVersion(INSTALLED_VERSION),
    )

    private class FixedConnectivity(private val isOnline: Boolean) : ConnectivityRepository {
        override fun isOnline(): Boolean = isOnline
    }

    private class CountingConfigRepository(private val config: AppConfig) : AppConfigRepository {
        var calls: Int = 0
            private set

        override suspend fun loadConfig(): AppResult<AppConfig> {
            calls++
            return AppResult.Success(config)
        }
    }

    private class FailingConfigRepository(
        private val failure: AppResult.Failure,
    ) : AppConfigRepository {
        override suspend fun loadConfig(): AppResult<AppConfig> = failure
    }

    private class FixedVersion(override val versionCode: Int) : AppVersionProvider

    private companion object {
        const val INSTALLED_VERSION = 4
        const val MESSAGE = "Estamos en mantenimiento"
    }
}
