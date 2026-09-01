package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.SearchQuery
import com.jrblanco.boccantabria.fake.FakeSearchRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPublicationsUseCaseTest {

    private val repository = FakeSearchRepository()
    private val useCase = SearchPublicationsUseCase(repository)

    @Test
    fun `a query too short never reaches the store`() = runTest {
        val results = useCase(SearchQuery(text = "a")).first()

        assertTrue(results.isEmpty)
        assertTrue(repository.queries.isEmpty())
    }

    @Test
    fun `an empty query never reaches the store either`() = runTest {
        useCase(SearchQuery(text = "   ")).first()

        assertTrue(repository.queries.isEmpty())
    }

    /**
     * One more than it will show. That extra row is the only thing that tells "exactly three
     * hundred" apart from "more than three hundred" without a second counting query.
     */
    @Test
    fun `it asks for one more row than it will show`() = runTest {
        useCase(SearchQuery(text = "pielagos")).first()

        assertEquals(listOf(SearchPublicationsUseCase.MAX_RESULTS + 1), repository.limits)
    }

    @Test
    fun `fewer results than the cap come back whole and untruncated`() = runTest {
        repository.emit(List(5) { publication(key = "boc:$it") })

        val results = useCase(SearchQuery(text = "pielagos")).first()

        assertEquals(5, results.items.size)
        assertFalse(results.isTruncated)
    }

    /** Exactly the cap is not truncated. This is what asking for one extra buys. */
    @Test
    fun `exactly the cap is not truncated`() = runTest {
        repository.emit(List(SearchPublicationsUseCase.MAX_RESULTS) { publication(key = "boc:$it") })

        val results = useCase(SearchQuery(text = "pielagos")).first()

        assertEquals(SearchPublicationsUseCase.MAX_RESULTS, results.items.size)
        assertFalse(results.isTruncated)
    }

    @Test
    fun `one over the cap is shown capped, and said out loud`() = runTest {
        repository.emit(List(SearchPublicationsUseCase.MAX_RESULTS + 1) { publication(key = "boc:$it") })

        val results = useCase(SearchQuery(text = "pielagos")).first()

        assertEquals(SearchPublicationsUseCase.MAX_RESULTS, results.items.size)
        assertTrue(results.isTruncated)
    }

    @Test
    fun `nothing found is an empty result and not a failure`() = runTest {
        repository.emit(emptyList())

        assertTrue(useCase(SearchQuery(text = "expropiacion")).first().isEmpty)
    }

    @Test
    fun `the query travels to the store as it was written`() = runTest {
        val query = SearchQuery(text = "pielagos", sectionCode = "3", issuer = "Gobierno de Cantabria")

        useCase(query).first()

        assertEquals(listOf(query), repository.queries)
    }
}
