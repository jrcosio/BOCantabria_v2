package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import androidx.core.net.toUri
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import com.jrblanco.boccantabria.core.telemetry.CrashReporter
import com.jrblanco.boccantabria.core.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The **second** frontier with `androidx.pdf`, and the only one outside `ui/pdf`.
 *
 * It replaces the text extractor of features 007 and 009 and keeps its place: counting pages is a
 * data source, not presentation. `PdfDocumentLoader` **stays** in `ui/pdf` all the same — moving it
 * here would break the rule that `ui` does not depend on `data` (009 research.md D-002).
 *
 * The document is opened through `SandboxedPdfLoader`, so it is parsed in the **separate process**
 * the viewer already uses. That matters more than it looks: these documents come from a public
 * service over the internet, and a malformed one must not be able to bring the application down.
 */
class AndroidxPdfPageCounter(
    context: Context,
    private val dispatchers: DispatcherProvider,
    private val crashReporter: CrashReporter,
) : PdfPageCounter {

    private val loader = SandboxedPdfLoader(context, dispatchers.io)

    override suspend fun pageCount(localPath: String): PageCountResult =
        withContext(dispatchers.io) {
            try {
                // Closed whatever happens: the document is read in another process, and leaking the
                // handle keeps that process alive holding a file the cache may want to evict.
                loader.openDocument(File(localPath).toUri()).use { document ->
                    val pages = document.pageCount
                    if (pages > 0) {
                        PageCountResult.Success(pages)
                    } else {
                        // Not a document we can cite pages of, and citing is the point.
                        PageCountResult.Failure(IllegalStateException("document has no pages"))
                    }
                }
            } catch (error: PdfPasswordException) {
                PageCountResult.Encrypted
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // The sandboxed process is short-lived and can be taken away mid-read; without this
                // line a `DeadObjectException` left no trace anywhere at all.
                crashReporter.log("pages failed: ${error.javaClass.simpleName}: ${error.message}")
                PageCountResult.Failure(error)
            }
        }
}

/**
 * Built here rather than in `core/di`, for the same reason as Room, OkHttp and the viewer's loader:
 * that package must not name a third-party SDK.
 */
fun pdfPageCounter(
    context: Context,
    dispatchers: DispatcherProvider,
    crashReporter: CrashReporter,
): PdfPageCounter = AndroidxPdfPageCounter(context, dispatchers, crashReporter)
