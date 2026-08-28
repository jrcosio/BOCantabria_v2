package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.data.repository.BocSectionRepositoryImpl
import org.junit.Assert.assertEquals
import org.junit.Test

class GetBocSectionsUseCaseTest {

    @Test
    fun `returns the whole tree in official order`() {
        val sections = GetBocSectionsUseCase(BocSectionRepositoryImpl())()

        assertEquals(23, sections.size)
        assertEquals("1", sections.first().code)
        assertEquals("9", sections.last().code)
    }

    @Test
    fun `it is a plain read, so two calls agree`() {
        val useCase = GetBocSectionsUseCase(BocSectionRepositoryImpl())

        assertEquals(useCase(), useCase())
    }
}
