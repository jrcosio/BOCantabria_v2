package com.jrblanco.boccantabria.data.source.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Placeholder local source: an in-memory cache.
 *
 * The persistence engine was deliberately deferred to the first business feature (see
 * research.md, D-001). Swapping this for a real database means changing this class only: the
 * repository talks to [ContentLocalDataSource], not to an implementation.
 *
 * Guarded by a mutex because concurrent loads are legitimate and the repository writes here.
 */
class InMemoryContentLocalDataSource : ContentLocalDataSource {

    private val mutex = Mutex()
    private var items: List<ContentItemEntity> = emptyList()

    override suspend fun readContentItems(): List<ContentItemEntity> = mutex.withLock { items }

    override suspend fun writeContentItems(items: List<ContentItemEntity>) = mutex.withLock {
        this.items = items
    }
}
