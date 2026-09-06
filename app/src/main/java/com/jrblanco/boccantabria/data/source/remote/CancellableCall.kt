package com.jrblanco.boccantabria.data.source.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs the call and consumes its response, cancelling the call if the coroutine is cancelled.
 *
 * Changing dispatcher does not make blocking I/O cancellable: `Call.execute()` inside
 * `withContext(io)` kept the socket and the thread busy until the response or the timeout — up to
 * three minutes for a document — and the audit measured `Call.isCanceled=false` after cancelling
 * (PERF-002). Only `Call.cancel()` closes the socket, and only `invokeOnCancellation` calls it at the
 * right moment. The body is consumed **inside** OkHttp's callback, which is where OkHttp documents it
 * should be read, so the cancellation covers the headers **and** the body: the downloader's 25 MB
 * write loop dies with an `IOException` as soon as the socket is closed.
 *
 * **Nothing may escape [Callback.onResponse].** `RealCall.AsyncCall.run` treats a non-`IOException`
 * leaving the callback as fatal: it cancels the call and rethrows it on the executor thread, which on
 * Android is an uncaught exception and a dead process. Every failure of [consume] — a `check`, a parse
 * error — is routed to the continuation instead. That `catch` is load-bearing, not defensive, and it
 * has a test (research.md D-617, D-618).
 *
 * Resuming an already-cancelled continuation is a silent no-op, and OkHttp calls exactly one of the
 * two callbacks, so no `isActive` check is needed: `T` carries no resource, because the `Response`
 * is closed by `use` before the coroutine resumes.
 *
 * [consume] MUST NOT suspend; it runs on OkHttp's thread. The dispatcher the caller was on is
 * restored when the coroutine resumes.
 */
internal suspend fun <T> Call.await(consume: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use(consume)
                } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
                    continuation.resumeWithException(failure)
                    return
                }
                continuation.resume(result)
            }
        },
    )
}
