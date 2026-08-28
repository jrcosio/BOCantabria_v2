package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigValues
import com.jrblanco.boccantabria.domain.model.AppConfig
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class AppConfigRepositoryImplTest {

    @Test
    fun `translates the remote values to domain`() = runTest {
        val values = RemoteConfigValues(minSupportedVersionCode = 7L, maintenanceMessage = "Aviso")

        val result = repository(SucceedingSource(values)).loadConfig()

        assertEquals(
            AppResult.Success(AppConfig(minSupportedVersionCode = 7, maintenanceMessage = "Aviso")),
            result,
        )
    }

    @Test
    fun `an empty maintenance message becomes null`() = runTest {
        // The remote service returns an empty string for an unset text. Normalising it here means
        // nothing downstream has to check for both null and blank.
        val values = RemoteConfigValues(minSupportedVersionCode = 1L, maintenanceMessage = "")

        val result = repository(SucceedingSource(values)).loadConfig()

        assertEquals(
            AppResult.Success(AppConfig(minSupportedVersionCode = 1, maintenanceMessage = null)),
            result,
        )
    }

    @Test
    fun `a blank maintenance message also becomes null`() = runTest {
        val values = RemoteConfigValues(minSupportedVersionCode = 1L, maintenanceMessage = "   ")

        val result = repository(SucceedingSource(values)).loadConfig()

        assertEquals(
            AppResult.Success(AppConfig(minSupportedVersionCode = 1, maintenanceMessage = null)),
            result,
        )
    }

    @Test
    fun `unpublished values arrive as the packaged defaults, and never block the app`() = runTest {
        // What the source returns when the console has nothing configured, which is the state of
        // the project today. Minimum version zero means "everything allowed".
        val values = RemoteConfigValues(minSupportedVersionCode = 0L, maintenanceMessage = "")

        val result = repository(SucceedingSource(values)).loadConfig()

        assertEquals(AppResult.Success(AppConfig()), result)
    }

    @Test
    fun `a source failure becomes a network failure, and no exception escapes`() = runTest {
        val result = repository(FailingSource(IOException("offline"))).loadConfig()

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    @Test
    fun `an unexpected error also stays inside the repository`() = runTest {
        val result = repository(FailingSource(IllegalStateException("boom"))).loadConfig()

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    private fun repository(source: RemoteConfigDataSource) = AppConfigRepositoryImpl(
        remoteConfigDataSource = source,
        dispatchers = TestDispatcherProvider(),
    )

    private class SucceedingSource(private val values: RemoteConfigValues) : RemoteConfigDataSource {
        override suspend fun fetchValues(): RemoteConfigValues = values
    }

    private class FailingSource(private val error: Throwable) : RemoteConfigDataSource {
        override suspend fun fetchValues(): RemoteConfigValues = throw error
    }
}
