package com.jrblanco.boccantabria.data.source.remote

interface ContentRemoteDataSource {

    /** May throw. The repository is the one that catches and translates. */
    suspend fun fetchContentItems(): List<ContentItemDto>
}
