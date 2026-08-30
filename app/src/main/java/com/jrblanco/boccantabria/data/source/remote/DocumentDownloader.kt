package com.jrblanco.boccantabria.data.source.remote

import java.io.File

/**
 * Fetches one official document into a local file.
 *
 * Reports refusal **as a value**, like [PublicationRemoteDataSource] and for the same reason: the
 * motive has to reach the top so it can be told, and an exception per way of distrusting a response
 * would turn the repository into a ladder of catches.
 *
 * The distrust is the point. The link returns a PDF today, but a public service with no availability
 * commitment can answer with an error page and a success code, and an application that consults an
 * official bulletin must not show that as official.
 */
interface DocumentDownloader {

    /**
     * @param into where to write. Left **untouched** unless every check passes.
     */
    suspend fun download(url: String, into: File): DownloadResult
}

sealed interface DownloadResult {

    data class Downloaded(val byteCount: Long, val checksum: String) : DownloadResult

    data class Rejected(val reason: DownloadRefusal) : DownloadResult
}

/**
 * Named `DownloadRefusal` and not `RejectionReason` because that name is taken: the feed reader
 * already rejects individual announcements with one. Two different distrusts, two names.
 */

/**
 * Why a response was not accepted as the official document.
 *
 * Split by whether retrying could help, which is the only decision made with it.
 */
sealed interface DownloadRefusal {

    /** The address is not https. Checked before opening a socket. */
    data object InsecureScheme : DownloadRefusal

    /** The address does not point at the bulletin's service. Checked before opening a socket. */
    data object UnexpectedHost : DownloadRefusal

    /** The service did not declare a PDF. Checked with the headers, before reading the body. */
    data object UnexpectedType : DownloadRefusal

    /** The bytes that arrived are not a PDF, whatever the headers said. */
    data object NotAPdf : DownloadRefusal

    /** Beyond the safety cap. The read stops rather than filling memory. */
    data object TooLarge : DownloadRefusal

    data class HttpError(val code: Int) : DownloadRefusal

    /** No route, timeout, connection lost. The one that is worth retrying. */
    data object Network : DownloadRefusal
}
