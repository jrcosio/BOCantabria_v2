package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.CoverageDto
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryDataSource
import com.jrblanco.boccantabria.data.source.remote.GeminiSummaryResult
import com.jrblanco.boccantabria.data.source.remote.SummaryUsage
import com.jrblanco.boccantabria.data.source.remote.UploadedDocument
import com.jrblanco.boccantabria.data.source.remote.ReferencedTextDto
import com.jrblanco.boccantabria.data.source.remote.SummaryPayload
import kotlinx.coroutines.CompletableDeferred

/**
 * The service, faked. [calls] is the number that matters most in these tests: several requirements
 * are about requests that must **not** happen.
 */
class FakeGeminiSummaryDataSource(
    var result: GeminiSummaryResult = GeminiSummaryResult.Success(
        payload = summaryPayload(),
        usage = SummaryUsage(
            totalInputTokens = 5_600,
            totalOutputTokens = 1_200,
            totalTokens = 6_800,
        ),
        systemFingerprint = null,
    ),
) : GeminiSummaryDataSource {

    /** Held open, the request stays in flight, which is what makes a transient state observable. */
    var gate: CompletableDeferred<Unit>? = null

    var calls: Int = 0
        private set
    var lastSystemMessage: String? = null
        private set
    var lastUserMessage: String? = null
        private set

    /** Which uploaded document the request pointed at. The point of feature 010. */
    var lastDocument: UploadedDocument? = null
        private set

    override suspend fun summarise(
        system: String,
        user: String,
        document: UploadedDocument,
    ): GeminiSummaryResult {
        calls++
        lastSystemMessage = system
        lastUserMessage = user
        lastDocument = document
        gate?.await()
        return result
    }
}

fun summaryPayload(
    plainLanguageSummary: String = "Se aprueba definitivamente la modificacion de la ordenanza.",
    coverage: CoverageDto = CoverageDto(pagesAnalyzed = listOf(1), totalPages = 1, complete = true),
) = SummaryPayload(
    documentTitle = "Aprobacion definitiva de la modificacion de la Ordenanza General",
    documentType = "Anuncio",
    issuingBody = "Ayuntamiento de Pielagos",
    plainLanguageSummary = plainLanguageSummary,
    keyPoints = listOf(ReferencedTextDto("Se aprueba la modificacion de la ordenanza", listOf(1))),
    coverage = coverage,
)
