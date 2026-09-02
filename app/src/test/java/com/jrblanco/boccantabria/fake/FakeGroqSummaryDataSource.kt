package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.CoverageDto
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryPayload
import com.jrblanco.boccantabria.data.source.remote.GroqSummaryResult
import com.jrblanco.boccantabria.data.source.remote.GroqUsage
import com.jrblanco.boccantabria.data.source.remote.ReferencedTextDto
import kotlinx.coroutines.CompletableDeferred

/**
 * The service, faked. [calls] is the number that matters most in these tests: several requirements
 * are about requests that must **not** happen.
 */
class FakeGroqSummaryDataSource(
    var result: GroqSummaryResult = GroqSummaryResult.Success(
        payload = summaryPayload(),
        usage = GroqUsage(promptTokens = 5_600, completionTokens = 1_200, totalTokens = 6_800),
        systemFingerprint = "fp_abc",
    ),
) : GroqSummaryDataSource {

    /** Held open, the request stays in flight, which is what makes a transient state observable. */
    var gate: CompletableDeferred<Unit>? = null

    var calls: Int = 0
        private set
    var lastSystemMessage: String? = null
        private set
    var lastUserMessage: String? = null
        private set

    override suspend fun summarise(
        system: String,
        user: String,
        estimatedTokens: Int,
    ): GroqSummaryResult {
        calls++
        lastSystemMessage = system
        lastUserMessage = user
        gate?.await()
        return result
    }
}

fun summaryPayload(
    plainLanguageSummary: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
    coverage: CoverageDto = CoverageDto(pagesAnalyzed = listOf(1), totalPages = 1, complete = true),
) = GroqSummaryPayload(
    documentTitle = "Aprobacion definitiva de la modificacion de la Ordenanza General",
    documentType = "Anuncio",
    issuingBody = "Ayuntamiento de Pielagos",
    plainLanguageSummary = plainLanguageSummary,
    keyPoints = listOf(ReferencedTextDto("Se aprueba la modificacion de la ordenanza", listOf(1))),
    coverage = coverage,
)
