package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.domain.model.AiSummaryConstants
import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The stored summaries, against a real in-memory database.
 *
 * The data-access object is never faked: faking it would mean reimplementing the statements under
 * test, and the two copies would drift.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiSummaryDaoTest {

    private lateinit var database: BocDatabase
    private lateinit var summaries: AiSummaryDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BocDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        summaries = database.aiSummaryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Having no summary is the normal state of a publication, not an error. */
    @Test
    fun `a publication without a summary emits null`() = runTest {
        assertNull(summaries.observe("boc:439765").first())
        assertNull(summaries.byExternalKey("boc:439765"))
    }

    @Test
    fun `what was stored comes back whole`() = runTest {
        summaries.upsert(entity())

        val stored = summaries.byExternalKey("boc:439765")

        assertEquals("a".repeat(64), stored?.pdfSha256)
        assertEquals(AiSummaryConstants.MODEL_ID, stored?.modelId)
        assertEquals(1_700_000_000_000L, stored?.createdAtEpochMillis)
        assertEquals(300, stored?.totalTokens)
        assertEquals("fp_abc", stored?.systemFingerprint)
    }

    /**
     * FR-034: regenerating replaces. An upsert and not a delete followed by an insert, because for
     * the moment between the two there would be no summary at all.
     */
    @Test
    fun `regenerating replaces the row instead of adding a second one`() = runTest {
        summaries.upsert(entity(summaryJson = """{"v":1}""", createdAt = 1_000L))
        summaries.upsert(entity(summaryJson = """{"v":2}""", createdAt = 2_000L))

        val stored = summaries.byExternalKey("boc:439765")

        assertEquals("""{"v":2}""", stored?.summaryJson)
        assertEquals(2_000L, stored?.createdAtEpochMillis)
    }

    @Test
    fun `two publications keep their own summaries`() = runTest {
        summaries.upsert(entity(externalKey = "boc:1", summaryJson = """{"a":1}"""))
        summaries.upsert(entity(externalKey = "boc:2", summaryJson = """{"b":2}"""))

        assertEquals("""{"a":1}""", summaries.byExternalKey("boc:1")?.summaryJson)
        assertEquals("""{"b":2}""", summaries.byExternalKey("boc:2")?.summaryJson)
    }

    @Test
    fun `a stored summary reaches whoever is already observing`() = runTest {
        summaries.upsert(entity())

        assertEquals("boc:439765", summaries.observe("boc:439765").first()?.externalKey)
    }

    /**
     * The fingerprint is nullable because the service does not always send it, and a summary that
     * arrives without one is still a summary.
     */
    @Test
    fun `a missing fingerprint is stored as absent`() = runTest {
        summaries.upsert(entity(fingerprint = null))

        assertNull(summaries.byExternalKey("boc:439765")?.systemFingerprint)
    }

    private fun entity(
        externalKey: String = "boc:439765",
        summaryJson: String = """{"plainLanguageSummary":"Se aprueba la ordenanza."}""",
        createdAt: Long = 1_700_000_000_000L,
        fingerprint: String? = "fp_abc",
    ) = AiSummaryEntity(
        externalKey = externalKey,
        pdfSha256 = "a".repeat(64),
        modelId = AiSummaryConstants.MODEL_ID,
        promptVersion = "boc-summary-es-v1",
        schemaVersion = "boc-summary-schema-v1",
        summaryJson = summaryJson,
        createdAtEpochMillis = createdAt,
        promptTokens = 100,
        completionTokens = 200,
        totalTokens = 300,
        systemFingerprint = fingerprint,
    )
}
