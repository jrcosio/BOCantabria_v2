package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import androidx.core.net.toUri
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.domain.model.PdfCorpus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The **second** frontier with `androidx.pdf`, and the only one outside `ui/pdf`.
 *
 * Extracting the text of a file is a data source, not presentation: putting it in `ui` would force
 * the view model to orchestrate the whole pipeline — download, extraction, budget, request,
 * validation — which is business logic in the presentation layer. `PdfDocumentLoader` **stays** in
 * `ui/pdf` all the same: moving it here would break the rule that `ui` does not depend on `data`
 * (research.md D-002).
 *
 * Text is pulled through `SandboxedPdfLoader`, so the parsing happens in the **separate process**
 * that the viewer already uses. That is what PdfBox would have cost: it would have parsed a document
 * fetched from a public service inside the application's own process (research.md D-001).
 */
class AndroidxPdfTextExtractor(
    context: Context,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : PdfTextExtractor {

    private val loader = SandboxedPdfLoader(context, dispatchers.io)

    override suspend fun extract(
        localPath: String,
        externalKey: String,
        pdfSha256: String,
    ): PdfExtractionResult = withContext(dispatchers.io) {
        try {
            // Closed whatever happens: the pages are read in another process, and leaking the
            // handle keeps that process alive holding a file the cache may want to evict.
            loader.openDocument(File(localPath).toUri()).use { document ->
                if (document.pageCount <= 0) return@use PdfExtractionResult.NoExtractableText

                val pages = (0 until document.pageCount).map { index ->
                    PdfCorpus.PdfPageText(
                        // The library counts from 0. This is the one place that conversion happens,
                        // so nothing above ever has to remember which convention it is looking at.
                        pageNumber = index + PAGE_NUMBER_OFFSET,
                        text = document.getPageContent(index)
                            ?.textContents
                            ?.joinToString(separator = "\n") { it.text }
                            .orEmpty(),
                    )
                }

                val corpus = PdfCorpus(
                    externalKey = externalKey,
                    pdfSha256 = pdfSha256,
                    totalPages = pages.size,
                    pages = pages,
                )

                if (corpus.hasUsableText) {
                    PdfExtractionResult.Success(corpus)
                } else {
                    PdfExtractionResult.NoExtractableText
                }
            }
        } catch (error: PdfPasswordException) {
            PdfExtractionResult.EncryptedPdf
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // The sandboxed process is short-lived and can be taken away mid-extraction; without
            // this line a `DeadObjectException` left no trace anywhere at all.
            crashReporter.log("extraction failed: ${error.javaClass.simpleName}: ${error.message}")
            PdfExtractionResult.Failure(error)
        }
    }

    private companion object {
        /** Pages are numbered from 1 outwards, as in any document. */
        const val PAGE_NUMBER_OFFSET = 1
    }
}

/**
 * Built here rather than in `core/di`, for the same reason as Room, OkHttp and the viewer's loader:
 * that package must not name a third-party SDK.
 */
fun pdfTextExtractor(
    context: Context,
    dispatchers: DispatcherProvider,
    crashReporter: CrashReporter,
): PdfTextExtractor = AndroidxPdfTextExtractor(context, dispatchers, crashReporter)
