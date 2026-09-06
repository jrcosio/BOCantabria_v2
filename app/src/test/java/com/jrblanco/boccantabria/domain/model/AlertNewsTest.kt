package com.jrblanco.boccantabria.domain.model

import com.jrblanco.boccantabria.fake.publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlertNewsTest {

    @Test
    fun `a piece of news is a publication with the rules that caught it`() {
        val news = AlertNews(publication("boc:1"), listOf("Ganadería", "Rural"), 9L, isRead = false)

        assertEquals(2, news.ruleNames.size)
        assertEquals(false, news.isRead)
    }

    @Test
    fun `news without a rule is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AlertNews(publication("boc:1"), emptyList(), 9L, isRead = false)
        }
    }
}
