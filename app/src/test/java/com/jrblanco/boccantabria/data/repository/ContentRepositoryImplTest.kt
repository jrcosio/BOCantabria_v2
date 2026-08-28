package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.local.ContentItemEntity
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentItemDto
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Covers the four rows of the repository policy table in contracts/internal-contracts.md, plus
 * the rule that no exception ever escapes the data layer.
 */
class ContentRepositoryImplTest {

    private val local = InMemoryLocal()

    @Test
    fun `remote responds - maps to domain, caches locally and returns success`() = runTest {
        val repository = repository(remote = SucceedingRemote(REMOTE_DTOS))

        val result = repository.getContentItems()

        assertEquals(AppResult.Success(REMOTE_ITEMS), result)
        // The DTO field is 'label' and the domain field is 'title': the mapping is real, not a
        // field-for-field copy, so a regression in it shows up here.
        assertEquals(listOf("Boletín del lunes", "Boletín del martes"), REMOTE_ITEMS.map { it.title })
        assertEquals(REMOTE_ENTITIES, local.stored)
    }

    @Test
    fun `remote fails with local fallback - returns the cached content`() = runTest {
        local.stored = REMOTE_ENTITIES
        val repository = repository(remote = FailingRemote())

        val result = repository.getContentItems()

        assertEquals(AppResult.Success(REMOTE_ITEMS), result)
    }

    @Test
    fun `remote fails without local fallback - returns network failure`() = runTest {
        val repository = repository(remote = FailingRemote())

        val result = repository.getContentItems()

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    @Test
    fun `remote returns an empty list - success with empty list and local cleared`() = runTest {
        local.stored = REMOTE_ENTITIES
        val repository = repository(remote = SucceedingRemote(emptyList()))

        val result = repository.getContentItems()

        assertEquals(AppResult.Success(emptyList<ContentItem>()), result)
        assertTrue(local.stored.isEmpty())
    }

    @Test
    fun `no exception escapes the repository`() = runTest {
        val repository = repository(remote = ExplodingRemote())

        val result = repository.getContentItems()

        assertEquals(AppResult.Failure(DomainError.Network), result)
    }

    private fun repository(remote: ContentRemoteDataSource) = ContentRepositoryImpl(
        remoteDataSource = remote,
        localDataSource = local,
        dispatchers = TestDispatcherProvider(),
    )

    private class SucceedingRemote(private val dtos: List<ContentItemDto>) : ContentRemoteDataSource {
        override suspend fun fetchContentItems(): List<ContentItemDto> = dtos
    }

    private class FailingRemote : ContentRemoteDataSource {
        override suspend fun fetchContentItems(): List<ContentItemDto> = throw IOException("offline")
    }

    private class ExplodingRemote : ContentRemoteDataSource {
        override suspend fun fetchContentItems(): List<ContentItemDto> = error("boom")
    }

    private class InMemoryLocal(var stored: List<ContentItemEntity> = emptyList()) : ContentLocalDataSource {
        override suspend fun readContentItems(): List<ContentItemEntity> = stored
        override suspend fun writeContentItems(items: List<ContentItemEntity>) {
            stored = items
        }
    }

    private companion object {
        val REMOTE_DTOS = listOf(
            ContentItemDto(id = "1", label = "Boletín del lunes"),
            ContentItemDto(id = "2", label = "Boletín del martes"),
        )
        val REMOTE_ITEMS = listOf(
            ContentItem(id = "1", title = "Boletín del lunes"),
            ContentItem(id = "2", title = "Boletín del martes"),
        )
        val REMOTE_ENTITIES = listOf(
            ContentItemEntity(id = "1", title = "Boletín del lunes"),
            ContentItemEntity(id = "2", title = "Boletín del martes"),
        )
    }
}
