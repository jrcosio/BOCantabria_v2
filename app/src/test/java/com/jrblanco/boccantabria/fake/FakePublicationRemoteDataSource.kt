package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.BocFeedDefinition
import com.jrblanco.boccantabria.data.source.remote.FeedFailure
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RssChannelDto
import com.jrblanco.boccantabria.data.source.remote.RssItemDto
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A source the test programmes feed by feed.
 *
 * It records the hash it was handed for each source —which is how the tests verify that an
 * unchanged source is never read twice— and how many reads overlapped, which is how they verify
 * the concurrency cap without watching the clock.
 */
class FakePublicationRemoteDataSource : PublicationRemoteDataSource {

    private val responses = ConcurrentHashMap<String, FeedFetchResult>()
    private val lock = Any()
    private var inFlight = 0
    private var failEverything: FeedFailure? = null

    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val hashesSeen: MutableMap<String, String> = ConcurrentHashMap()

    var maxConcurrent: Int = 0
        private set

    fun respondWith(feedId: String, result: FeedFetchResult) {
        responses[feedId] = result
    }

    fun respondWithItems(feedId: String, bodyHash: String, vararg items: RssItemDto) {
        responses[feedId] = FeedFetchResult.Fetched(
            channel = RssChannelDto("Filtro BOC", null, null, items.size, items.toList()),
            bodyHash = bodyHash,
        )
    }

    fun failEveryFeed(cause: FeedFailure = FeedFailure.NETWORK) {
        responses.clear()
        failEverything = cause
    }

    override suspend fun fetchFeed(
        definition: BocFeedDefinition,
        knownBodyHash: String?,
    ): FeedFetchResult {
        synchronized(lock) {
            inFlight++
            maxConcurrent = maxOf(maxConcurrent, inFlight)
        }
        try {
            calls += definition.feedId
            knownBodyHash?.let { hashesSeen[definition.feedId] = it }

            failEverything?.let { return FeedFetchResult.Failed(it) }

            val programmed = responses[definition.feedId] ?: EMPTY_CHANNEL
            // Mirrors what the real source does: identical bytes are reported as unchanged and
            // never handed on to be parsed again.
            if (programmed is FeedFetchResult.Fetched && programmed.bodyHash == knownBodyHash) {
                return FeedFetchResult.NotModified
            }
            return programmed
        } finally {
            synchronized(lock) { inFlight-- }
        }
    }

    private companion object {
        val EMPTY_CHANNEL = FeedFetchResult.Fetched(
            channel = RssChannelDto("Filtro BOC", null, null, 0, emptyList()),
            bodyHash = "canal-vacio",
        )
    }
}

/** An announcement as a source would publish it. */
fun rssItem(
    blobId: String,
    title: String = "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva.",
    date: String = "2026-08-27",
    categories: String? = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
) = RssItemDto(
    title = title,
    link = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=$blobId",
    pubDateRaw = date,
    categoriesRaw = categories,
)
