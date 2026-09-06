package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.fake.FakeNotificationStatusRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class GetNotificationStatusUseCaseTest {

    @Test
    fun `passes the platform's answer through`() {
        val repository = FakeNotificationStatusRepository(NotificationStatus.DISABLED)

        assertEquals(NotificationStatus.DISABLED, GetNotificationStatusUseCase(repository)())
    }
}
