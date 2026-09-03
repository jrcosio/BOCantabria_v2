package com.jrblanco.boccantabria.ui.info

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.fake.RecordingAnalyticsTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoViewModelTest {

    private val analytics = RecordingAnalyticsTracker()
    private val viewModel = InfoViewModel(
        appVersion = FixedVersion,
        analytics = analytics,
    )

    @Test
    fun `the state exposes the installed version name`() {
        assertEquals("8.1.0-test", viewModel.uiState.value.versionName)
    }

    @Test
    fun `opening the screen reports one screen view`() {
        assertEquals(listOf(InfoViewModel.SCREEN_NAME), analytics.screenViews)
    }

    @Test
    fun `each external destination is reported without its url`() {
        viewModel.onLinkTapped(InfoLink.LINKEDIN)
        viewModel.onLinkTapped(InfoLink.GITHUB)

        val linkEvents = analytics.events.filter { it.name == InfoViewModel.EVENT_LINK_TAPPED }
        assertEquals(2, linkEvents.size)
        assertEquals("linkedin", linkEvents[0].parameters[InfoViewModel.PARAM_DESTINATION])
        assertEquals("github", linkEvents[1].parameters[InfoViewModel.PARAM_DESTINATION])
        assertFalse(linkEvents.any { event -> event.parameters.values.any { it.startsWith("https://") } })
    }

    @Test
    fun `a link failure is exposed once and can be consumed`() {
        viewModel.onLinkOpenFailed()
        assertTrue(viewModel.uiState.value.linkOpenFailed)

        viewModel.onLinkErrorConsumed()
        assertFalse(viewModel.uiState.value.linkOpenFailed)
    }

    private object FixedVersion : AppVersionProvider {
        override val versionCode: Int = 81
        override val versionName: String = "8.1.0-test"
    }
}
