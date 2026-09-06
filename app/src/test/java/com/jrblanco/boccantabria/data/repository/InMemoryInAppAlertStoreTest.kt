package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.domain.model.InAppAlert
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryInAppAlertStoreTest {

    @Test
    fun `starts empty, holds what is published, and accumulates`() = runTest {
        val store = InMemoryInAppAlertStore()
        assertNull(store.observePending().first())

        store.publish(InAppAlert(1, "Ganadería"))
        assertEquals(InAppAlert(1, "Ganadería"), store.observePending().first())

        store.publish(InAppAlert(2, null))
        assertEquals(InAppAlert(3, null), store.observePending().first())
    }

    @Test
    fun `consuming clears it`() = runTest {
        val store = InMemoryInAppAlertStore().apply { publish(InAppAlert(1, null)) }

        store.consume()

        assertNull(store.observePending().first())
    }
}
