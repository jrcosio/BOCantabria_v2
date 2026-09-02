package com.jrblanco.boccantabria.ui.detail

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AiSummaryError
import com.jrblanco.boccantabria.ui.detail.component.messageRes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SC-008 and FR-040, turned from wording into a guarantee.
 *
 * «No status codes, no traces, no wording from the provider» is the kind of rule that holds until
 * somebody pastes a message from a stack trace into a string because it was quicker. This is what
 * notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiErrorMessagesTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val everyError = listOf(
        AiSummaryError.Offline,
        AiSummaryError.NoExtractableText,
        AiSummaryError.EncryptedPdf,
        AiSummaryError.QuotaMinute(secondsRemaining = 42),
        AiSummaryError.QuotaDay,
        AiSummaryError.NotConfigured,
        AiSummaryError.InvalidResponse,
        AiSummaryError.Unknown,
    )

    @Test
    fun `every error has a message of its own`() {
        val messages = everyError.map { context.getString(it.messageRes()) }

        assertEquals(everyError.size, messages.distinct().size)
        messages.forEach { message -> assertTrue(message.isNotBlank()) }
    }

    /** A three-digit number in a user-facing message is an HTTP code that escaped. */
    @Test
    fun `no message shows a status code`() {
        everyError.forEach { error ->
            val message = context.getString(error.messageRes())
            assertFalse(
                "«$message» parece llevar un código de estado",
                Regex("\\b[45]\\d{2}\\b").containsMatchIn(message),
            )
        }
    }

    @Test
    fun `no message names the provider or the technology behind it`() {
        val forbidden = listOf("groq", "qwen", "http", "json", "api", "token", "sdk", "null")

        everyError.forEach { error ->
            val message = context.getString(error.messageRes()).lowercase()
            forbidden.forEach { word ->
                assertFalse("«$message» menciona «$word»", message.contains(word))
            }
        }
    }

    /** A trace that reached a message would arrive with these in it. */
    @Test
    fun `no message carries a trace or an exception name`() {
        everyError.forEach { error ->
            val message = context.getString(error.messageRes())
            assertFalse(message.contains("Exception"))
            assertFalse(message.contains("at com."))
            assertFalse(message.contains("java."))
        }
    }

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())
}
