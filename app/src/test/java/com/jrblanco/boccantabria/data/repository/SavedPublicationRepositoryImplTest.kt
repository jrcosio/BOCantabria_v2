package com.jrblanco.boccantabria.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.jrblanco.boccantabria.core.util.TimeProvider
import com.jrblanco.boccantabria.data.source.local.BocDatabase
import com.jrblanco.boccantabria.data.source.local.SavedPublicationDao
import com.jrblanco.boccantabria.data.source.local.toEntity
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import com.jrblanco.boccantabria.fake.RecordingCrashReporter
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import com.jrblanco.boccantabria.fake.publication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The saved-mark policy, exercised against a real in-memory database.
 *
 * Faking the data-access object would mean reimplementing the statements under test, and the two
 * copies would drift. Same reasoning as `PublicationRepositoryImplTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class SavedPublicationRepositoryImplTest {

    private lateinit var database: BocDatabase
    private val analytics = RecordingAnalyticsTracker()
    private var now = 5_000L
    private val time = object : TimeProvider {
        override fun nowMillis(): Long = now
    }

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `nothing saved is an empty result, not a failure`() = runTest {
        storePublications("boc:1", "boc:2")
        val repository = repository()

        assertEquals(emptyList<Any>(), repository.observeSaved().first())
        assertEquals(emptySet<String>(), repository.observeSavedKeys().first())
    }

    @Test
    fun `saving writes the injected instant and the publication comes back as domain`() = runTest {
        storePublications("boc:1")
        val repository = repository()

        val result = repository.setSaved("boc:1", saved = true)

        assertTrue(result is AppResult.Success)
        val saved = repository.observeSaved().first().single()
        assertEquals("boc:1", saved.externalKey)
        // Es un modelo de dominio, no una entidad: la capa de datos no cruza hacia arriba.
        assertEquals(
            "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.",
            saved.title,
        )
        assertEquals(setOf("boc:1"), repository.observeSavedKeys().first())
    }

    /** El orden lo pone el almacén, y por eso el tiempo se inyecta: si no, habría que esperar. */
    @Test
    fun `the order is the instant of the mark, most recent first`() = runTest {
        storePublications("boc:1", "boc:2", "boc:3")
        val repository = repository()

        now = 1_000L
        repository.setSaved("boc:1", saved = true)
        now = 3_000L
        repository.setSaved("boc:2", saved = true)
        now = 2_000L
        repository.setSaved("boc:3", saved = true)

        assertEquals(
            listOf("boc:2", "boc:3", "boc:1"),
            repository.observeSaved().first().map { it.externalKey },
        )
    }

    @Test
    fun `unsaving clears the mark and leaves the publication stored`() = runTest {
        storePublications("boc:1")
        val repository = repository()
        repository.setSaved("boc:1", saved = true)

        val result = repository.setSaved("boc:1", saved = false)

        assertTrue(result is AppResult.Success)
        assertEquals(emptySet<String>(), repository.observeSavedKeys().first())
        assertEquals(1, database.publicationDao().count())
    }

    @Test
    fun `a key that is not stored succeeds without creating anything`() = runTest {
        val repository = repository()

        val result = repository.setSaved("boc:missing", saved = true)

        assertTrue(result is AppResult.Success)
        assertEquals(emptySet<String>(), repository.observeSavedKeys().first())
    }

    @Test
    fun `a write failure travels as a failure and never as an exception`() = runTest {
        val brokenDao = mockk<SavedPublicationDao>()
        coEvery { brokenDao.setSavedAt(any(), any()) } throws IllegalStateException("disco lleno")
        val repository = repository(dao = brokenDao)

        val result = repository.setSaved("boc:1", saved = true)

        assertEquals(AppResult.Failure(DomainError.Unknown), result)
    }

    /**
     * Un fallo de lectura no puede terminar el flujo: la pantalla se quedaría vacía para siempre.
     *
     * Feature 014 (STAB-004): hasta entonces esta prueba usaba `.first()`, que toma el primer valor y
     * cancela, y por eso nunca vio que el flujo **terminaba** tras el vacío. Se afirma sobre lo que
     * llega después de recuperarse, y el doble falla una sola vez —`retryWhen` re-colecciona el mismo
     * objeto, así que el fallo se cuenta dentro del constructor del flujo—.
     */
    @Test
    fun `a read failure emits empty and keeps observing`() = runTest {
        storePublications("boc:1")
        database.savedPublicationDao().setSavedAt("boc:1", 1_000L)
        val real = database.savedPublicationDao()
        var attempts = 0
        val brokenOnce = mockk<SavedPublicationDao>()
        every { brokenOnce.observeSaved() } returns flow {
            if (attempts++ == 0) throw IllegalStateException("base ocupada")
            emitAll(real.observeSaved())
        }
        val repository = repository(dao = brokenOnce)

        repository.observeSaved().test {
            assertEquals(emptyList<Any>(), awaitItem())
            assertEquals(listOf("boc:1"), awaitItem().map { it.externalKey })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, crashReporter.nonFatals.size)
    }

    @Test
    fun `the reported event says whether it was saved and nothing else`() = runTest {
        storePublications("boc:1")
        val repository = repository()

        repository.setSaved("boc:1", saved = true)
        repository.setSaved("boc:1", saved = false)

        val events = analytics.events.filter { it.name == SavedPublicationRepositoryImpl.EVENT_SAVE }
        assertEquals(2, events.size)
        assertEquals(mapOf("saved" to "true"), events[0].parameters)
        assertEquals(mapOf("saved" to "false"), events[1].parameters)
        // Ni la clave, ni el título, ni la sección: qué guarda una persona es una señal de interés
        // personal, y el principio VI lo prohíbe (FR-025).
        val reported = events.flatMap { it.parameters.values }
        assertFalse(reported.any { it.contains("boc:") })
        assertFalse(reported.any { it.contains("PIÉLAGOS", ignoreCase = true) })
    }

    private val crashReporter = RecordingCrashReporter()

    private fun repository(
        dao: SavedPublicationDao = database.savedPublicationDao(),
    ) = SavedPublicationRepositoryImpl(
        savedPublicationDao = dao,
        time = time,
        dispatchers = TestDispatcherProvider(),
        analytics = analytics,
        crashReporter = crashReporter,
    )

    private suspend fun storePublications(vararg keys: String) {
        database.publicationDao().upsertAll(
            keys.map { publication(key = it).toEntity(seenAt = 1_000L, searchText = "") },
        )
    }
}
