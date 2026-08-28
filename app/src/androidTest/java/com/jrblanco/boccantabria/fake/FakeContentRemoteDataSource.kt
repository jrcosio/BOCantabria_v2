package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.remote.ContentItemDto
import com.jrblanco.boccantabria.data.source.remote.ContentRemoteDataSource

/**
 * Remote source under the test's control. Instrumented tests cannot see `src/test`, so this
 * mirrors the double used by the unit tests.
 */
class FakeContentRemoteDataSource(
    private val dtos: List<ContentItemDto> = DEFAULT_DTOS,
    private val failure: Throwable? = null,
) : ContentRemoteDataSource {

    var calls: Int = 0
        private set

    override suspend fun fetchContentItems(): List<ContentItemDto> {
        calls++
        failure?.let { throw it }
        return dtos
    }

    companion object {
        val DEFAULT_DTOS: List<ContentItemDto> = listOf(
            ContentItemDto(id = "1", label = "Boletín de prueba"),
        )
    }
}
