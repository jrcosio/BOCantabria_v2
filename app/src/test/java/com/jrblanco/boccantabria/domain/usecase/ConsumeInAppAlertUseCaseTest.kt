package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.fake.FakeInAppAlertStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsumeInAppAlertUseCaseTest {

    @Test
    fun `consuming clears what was pending`() = runTest {
        val store = FakeInAppAlertStore().apply { publish(InAppAlert(2, null)) }

        ConsumeInAppAlertUseCase(store)()

        assertNull(store.observePending().first())
        assertEquals(1, store.consumed)
    }
}
