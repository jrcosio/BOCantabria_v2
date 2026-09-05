package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Three values, and the count is the point.
 *
 * Two would have thrown «the document does not say» in with «write me a poem», and those are
 * opposites: the first is the correct answer to a fair question (011 research.md D-308). If anyone
 * collapses them later, this test says why they were separate.
 */
class AiAnswerScopeTest {

    @Test
    fun `there are exactly three scopes`() {
        assertEquals(3, AiAnswerScope.entries.size)
    }

    @Test
    fun `the three are the ones the defence is built on`() {
        assertEquals(
            listOf(
                AiAnswerScope.FROM_DOCUMENT,
                AiAnswerScope.NOT_IN_DOCUMENT,
                AiAnswerScope.OUT_OF_SCOPE,
            ),
            AiAnswerScope.entries,
        )
    }

    @Test
    fun `the names are what travels in the schema, so they must not drift`() {
        assertEquals("FROM_DOCUMENT", AiAnswerScope.FROM_DOCUMENT.name)
        assertEquals("NOT_IN_DOCUMENT", AiAnswerScope.NOT_IN_DOCUMENT.name)
        assertEquals("OUT_OF_SCOPE", AiAnswerScope.OUT_OF_SCOPE.name)
    }
}
