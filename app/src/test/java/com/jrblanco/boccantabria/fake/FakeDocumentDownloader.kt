package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.DocumentDownloader
import com.jrblanco.boccantabria.data.source.remote.DownloadRefusal
import com.jrblanco.boccantabria.data.source.remote.DownloadResult
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * A downloader the test drives.
 *
 * [gate] is what makes the deduplication testable: with it held, two callers are guaranteed to be
 * in flight at the same time, which is the situation the repository has to survive.
 */
class FakeDocumentDownloader(
    private val content: ByteArray = "%PDF-1.7\nBoletín".toByteArray(),
) : DocumentDownloader {

    var result: DownloadResult? = null
    var gate: CompletableDeferred<Unit>? = null

    private val counter = AtomicInteger()
    val calls: Int get() = counter.get()

    override suspend fun download(url: String, into: File): DownloadResult {
        counter.incrementAndGet()
        gate?.await()

        val outcome = result ?: DownloadResult.Downloaded(
            byteCount = content.size.toLong(),
            checksum = "a".repeat(64),
        )
        if (outcome is DownloadResult.Downloaded) {
            into.parentFile?.mkdirs()
            into.writeBytes(content)
        }
        return outcome
    }

    fun refuse(reason: DownloadRefusal) {
        result = DownloadResult.Rejected(reason)
    }
}
