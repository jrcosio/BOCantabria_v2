package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The use case adds no logic today: it exists so the presentation layer never knows about
 * repositories, and so there is an obvious place to put the logic when it appears. What is
 * worth protecting is precisely that it does not distort what the repository returns.
 */
class GetContentItemsUseCaseTest {

    @Test
    fun `propagates success unchanged`() = runTest {
        val items = listOf(ContentItem(id = "1", title = "Uno"))
        val expected = AppResult.Success(items)
        val useCase = GetContentItemsUseCase(FixedRepository(expected))

        val actual = useCase()

        assertSame(expected, actual)
    }

    @Test
    fun `propagates empty success as success, not as failure`() = runTest {
        val useCase = GetContentItemsUseCase(FixedRepository(AppResult.Success(emptyList())))

        val actual = useCase()

        assertEquals(AppResult.Success(emptyList<ContentItem>()), actual)
    }

    @Test
    fun `propagates failure unchanged`() = runTest {
        val expected = AppResult.Failure(DomainError.Network)
        val useCase = GetContentItemsUseCase(FixedRepository(expected))

        val actual = useCase()

        assertSame(expected, actual)
    }

    private class FixedRepository(
        private val result: AppResult<List<ContentItem>>,
    ) : ContentRepository {
        override suspend fun getContentItems(): AppResult<List<ContentItem>> = result
    }
}
