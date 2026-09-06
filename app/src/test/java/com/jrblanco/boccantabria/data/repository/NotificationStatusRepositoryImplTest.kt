package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.local.NotificationStatusDataSource
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationStatusRepositoryImplTest {

    @Test
    fun `passes the platform's answer through`() {
        val source = object : NotificationStatusDataSource {
            override fun status() = NotificationStatus.NEEDS_REQUEST
        }

        assertEquals(NotificationStatus.NEEDS_REQUEST, NotificationStatusRepositoryImpl(source).status())
    }
}
