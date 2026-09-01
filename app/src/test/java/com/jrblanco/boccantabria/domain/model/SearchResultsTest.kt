package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.jrblanco.boccantabria.fake.publication
import org.junit.Test

class SearchResultsTest {

    @Test
    fun `no results is a normal answer and not a truncated one`() {
        assertTrue(SearchResults.EMPTY.isEmpty)
        assertFalse(SearchResults.EMPTY.isTruncated)
    }

    @Test
    fun `results are not truncated unless somebody says so`() {
        assertFalse(SearchResults(listOf(publication())).isTruncated)
    }

    @Test
    fun `a truncated result still carries what it did find`() {
        val results = SearchResults(listOf(publication()), isTruncated = true)

        assertTrue(results.isTruncated)
        assertFalse(results.isEmpty)
    }

}
