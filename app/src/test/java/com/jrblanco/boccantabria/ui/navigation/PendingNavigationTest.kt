package com.jrblanco.boccantabria.ui.navigation

import android.app.Application
import android.content.Intent
import com.jrblanco.boccantabria.core.notification.AlertIntentExtras
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a notification's intent turns into, and that a pending destination is consumed once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class PendingNavigationTest {

    @Test
    fun `a publication target carries its key`() {
        val intent = Intent()
            .putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_PUBLICATION)
            .putExtra(AlertIntentExtras.EXTRA_EXTERNAL_KEY, "boc:1")

        assertEquals(PendingNavigation.Publication("boc:1"), intent.toPendingNavigation())
    }

    @Test
    fun `a publication target without a key is nothing`() {
        val intent = Intent().putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_PUBLICATION)

        assertNull(intent.toPendingNavigation())
    }

    @Test
    fun `the news target lands on the news`() {
        val intent = Intent().putExtra(AlertIntentExtras.EXTRA_TARGET, AlertIntentExtras.TARGET_NEWS)

        assertEquals(PendingNavigation.AlertNews, intent.toPendingNavigation())
    }

    @Test
    fun `the launcher intent is nothing`() {
        assertNull(Intent(Intent.ACTION_MAIN).toPendingNavigation())
    }

    @Test
    fun `the store hands a destination out once`() {
        val store = PendingNavigationStore()
        store.set(PendingNavigation.AlertNews)

        assertEquals(PendingNavigation.AlertNews, store.pending.value)
        assertEquals(PendingNavigation.AlertNews, store.consume())
        assertNull(store.pending.value)
        assertNull(store.consume())
    }
}
