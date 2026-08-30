package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.ShareTarget
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository
import com.jrblanco.boccantabria.fake.FakeDocumentRepository
import com.jrblanco.boccantabria.fake.publication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the rule for the degraded case lives, and the only place it should.
 */
class ShareOfficialDocumentUseCaseTest {

    private val documents = FakeDocumentRepository()

    @Test
    fun `a document that can be obtained is what gets shared`() = runTest {
        val result = useCase(online = true)(publication())

        assertTrue((result as AppResult.Success).data is ShareTarget.Document)
    }

    @Test
    fun `without connection and without a copy, the link is offered with its reason`() = runTest {
        documents.result = AppResult.Failure(DomainError.Network)

        val target = (useCase(online = false)(publication()) as AppResult.Success).data

        val link = target as ShareTarget.Link
        assertEquals(ShareTarget.LinkReason.NO_CONNECTION, link.reason)
        assertEquals(publication().documentUrl, link.url)
    }

    @Test
    fun `a network failure while online is a failure, not a link`() = runTest {
        // The service being down is not the same as the person being offline, and quietly sending
        // a link would hide the difference.
        documents.result = AppResult.Failure(DomainError.Network)

        assertEquals(
            AppResult.Failure(DomainError.Network),
            useCase(online = true)(publication()),
        )
    }

    @Test
    fun `a document that turned out not to be one never degrades to its link`() = runTest {
        // Sending the link of something the service returned wrong would hide that it is wrong.
        documents.result = AppResult.Failure(DomainError.Unknown)

        assertEquals(
            AppResult.Failure(DomainError.Unknown),
            useCase(online = false)(publication()),
        )
    }

    private fun useCase(online: Boolean) = ShareOfficialDocumentUseCase(
        documents = documents,
        connectivity = object : ConnectivityRepository {
            override fun isOnline(): Boolean = online
        },
    )
}
