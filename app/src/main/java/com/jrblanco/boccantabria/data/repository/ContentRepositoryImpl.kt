package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.core.util.DispatcherProvider
import com.jrblanco.boccantabria.data.source.local.ContentItemEntity
import com.jrblanco.boccantabria.data.source.local.ContentLocalDataSource
import com.jrblanco.boccantabria.data.source.remote.ContentItemDto
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource
import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.repository.ContentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Remote first, local as fallback.
 *
 * This is the policy every future feature will inherit, which is why the placeholder sources
 * come in a pair instead of a single one: with only one source there is no policy to test.
 *
 * Exceptions stop here. Nothing above this class ever sees a [Throwable].
 */
class ContentRepositoryImpl(
    private val remoteDataSource: ContentRemoteDataSource,
    private val localDataSource: ContentLocalDataSource,
    private val dispatchers: DispatcherProvider,
) : ContentRepository {

    override suspend fun getContentItems(): AppResult<List<ContentItem>> =
        withContext(dispatchers.io) {
            try {
                val remoteItems = remoteDataSource.fetchContentItems().map { it.toDomain() }
                localDataSource.writeContentItems(remoteItems.map { it.toEntity() })
                AppResult.Success(remoteItems)
            } catch (cancellation: CancellationException) {
                // Cancellation is not a failure of the operation: it must keep propagating or
                // structured concurrency stops working.
                throw cancellation
            } catch (_: Throwable) {
                val cached = localDataSource.readContentItems()
                if (cached.isEmpty()) {
                    AppResult.Failure(DomainError.Network)
                } else {
                    AppResult.Success(cached.map { it.toDomain() })
                }
            }
        }
}

private fun ContentItemDto.toDomain() = ContentItem(id = id, title = label)

private fun ContentItemEntity.toDomain() = ContentItem(id = id, title = title)

private fun ContentItem.toEntity() = ContentItemEntity(id = id, title = title)
