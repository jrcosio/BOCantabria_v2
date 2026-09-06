package com.jrblanco.boccantabria.domain.usecase

import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.fake.FakeInAppAlertStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObservePendingInAppAlertUseCaseTest {

    @Test
    fun `nothing pending is null, and a publication shows up`() = runTest {
        val store = FakeInAppAlertStore()

        assertNull(ObservePendingInAppAlertUseCase(store)().first())
        store.publish(InAppAlert(1, "Ganadería"))
        assertEquals(InAppAlert(1, "Ganadería"), ObservePendingInAppAlertUseCase(store)().first())
    }
}
