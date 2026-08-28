package com.jrblanco.boccantabria.data.source.remote

import kotlinx.coroutines.delay

/**
 * Placeholder remote source: a fixed list behind a simulated latency.
 *
 * The choice of HTTP client was deliberately deferred to the first business feature (see
 * research.md, D-001). The latency is here so the loading state is actually reachable and
 * therefore testable, rather than a branch nobody ever sees.
 */
class StubContentRemoteDataSource : ContentRemoteDataSource {

    override suspend fun fetchContentItems(): List<ContentItemDto> {
        delay(SIMULATED_LATENCY_MILLIS)
        return ITEMS
    }

    private companion object {
        const val SIMULATED_LATENCY_MILLIS = 400L

        val ITEMS = listOf(
            ContentItemDto(id = "1", label = "Boletín Oficial de Cantabria"),
            ContentItemDto(id = "2", label = "Últimas disposiciones"),
            ContentItemDto(id = "3", label = "Anuncios y licitaciones"),
        )
    }
}
