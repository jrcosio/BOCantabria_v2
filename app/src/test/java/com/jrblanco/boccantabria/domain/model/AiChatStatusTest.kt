package com.jrblanco.boccantabria.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiChatStatusTest {

    @Test
    fun `there are exactly two preparation phases, the same two the summary shows`() {
        assertEquals(2, AiChatStatus.Preparing.Phase.entries.size)
        assertEquals(
            listOf(
                AiChatStatus.Preparing.Phase.FETCHING_DOCUMENT,
                AiChatStatus.Preparing.Phase.UPLOADING_DOCUMENT,
            ),
            AiChatStatus.Preparing.Phase.entries,
        )
    }

    @Test
    fun `a retryable failure points at the question that would be resent`() {
        val status = AiChatStatus.Failed(AiChatError.Offline, retryableQuestionId = "q7")

        assertEquals("q7", status.retryableQuestionId)
    }

    @Test
    fun `a failure that cannot be retried points at nothing`() {
        val status = AiChatStatus.Failed(AiChatError.NotConfigured, retryableQuestionId = null)

        assertNull(status.retryableQuestionId)
    }

    @Test
    fun `idle and thinking are objects, so two references are the same state`() {
        assertEquals(AiChatStatus.Idle, AiChatStatus.Idle)
        assertEquals(AiChatStatus.Thinking, AiChatStatus.Thinking)
    }
}
