package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.BocFeedDefinition
import com.jrblanco.boccantabria.data.source.remote.FeedFailure
import com.jrblanco.boccantabria.data.source.remote.FeedFetchResult
import com.jrblanco.boccantabria.data.source.remote.PublicationRemoteDataSource
import com.jrblanco.boccantabria.data.source.remote.RssChannelDto
import com.jrblanco.boccantabria.data.source.remote.RssItemDto

/**
 * A bulletin under the test's control. Instrumented tests cannot see `src/test`, so this mirrors
 * the double the unit tests use.
 */
class FakeBocRemoteDataSource(
    private val itemsByFeed: Map<String, List<RssItemDto>> = DEFAULT_ITEMS,
    @Volatile var failure: FeedFailure? = null,
) : PublicationRemoteDataSource {

    @Volatile
    var calls: Int = 0
        private set

    override suspend fun fetchFeed(
        definition: BocFeedDefinition,
        knownBodyHash: String?,
    ): FeedFetchResult {
        calls++
        failure?.let { return FeedFetchResult.Failed(it) }

        val items = itemsByFeed[definition.feedId].orEmpty()
        return FeedFetchResult.Fetched(
            channel = RssChannelDto("Filtro BOC", null, null, items.size, items),
            bodyHash = "${definition.feedId}-${items.size}",
        )
    }

    companion object {
        // What the source publishes: it prefixes most titles with the issuing body.
        const val DISPOSICIONES_TITLE =
            "AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal."
        const val OPOSICIONES_TITLE =
            "AYUNTAMIENTO DE SANTOÑA: Bases de la convocatoria para cubrir una plaza."

        // What a card shows: the issuer already has its own line above, so repeating it in the
        // title would say the same name twice in a row.
        const val DISPOSICIONES_DISPLAYED = "Aprobación definitiva de la Ordenanza Fiscal."
        const val OPOSICIONES_DISPLAYED = "Bases de la convocatoria para cubrir una plaza."

        val DEFAULT_ITEMS: Map<String, List<RssItemDto>> = mapOf(
            "6802081" to listOf(
                RssItemDto(
                    title = DISPOSICIONES_TITLE,
                    link = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
                    pubDateRaw = "2026-08-27",
                    categoriesRaw = "1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD",
                ),
            ),
            "6802085" to listOf(
                RssItemDto(
                    title = OPOSICIONES_TITLE,
                    link = "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439700",
                    pubDateRaw = "2026-08-20",
                    categoriesRaw = "2.Autoridades y Personal|2.2.Cursos, Oposiciones y Concursos|" +
                        "Ayuntamiento de Santoña|ORD",
                ),
            ),
        )
    }
}
