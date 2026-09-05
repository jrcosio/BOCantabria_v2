package com.jrblanco.boccantabria.ui.ask

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.domain.model.AiChatError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FR-031 and SC-006, turned from wording into a guarantee.
 *
 * Calqued from `AiErrorMessagesTest`, and the duplication is deliberate: this is a **second** set of
 * eight strings, written by hand, and the rule they have to obey is the kind that holds until somebody
 * pastes a message from a stack trace into one because it was quicker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AskErrorMessagesTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val everyError = listOf(
        AiChatError.Offline,
        AiChatError.QuotaMinute(secondsRemaining = 37),
        AiChatError.QuotaDay,
        AiChatError.NotConfigured,
        AiChatError.UnreadableDocument,
        AiChatError.EncryptedPdf,
        AiChatError.InvalidResponse,
        AiChatError.Unknown,
    )

    @Test
    fun `all eight errors have a message of their own`() {
        val messages = everyError.map { context.getString(it.messageRes()) }

        assertTrue(messages.size == 8)
        assertTrue("dos errores comparten frase", messages.distinct().size == messages.size)
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
        val forbidden = listOf(
            "groq", "qwen", "gemini", "google",
            "http", "json", "api", "token", "sdk", "null",
        )

        everyError.forEach { error ->
            val message = context.getString(error.messageRes()).lowercase()
            forbidden.forEach { word ->
                assertFalse("«$message» menciona «$word»", message.contains(word))
            }
        }
    }

    @Test
    fun `no message carries a trace or an exception name`() {
        everyError.forEach { error ->
            val message = context.getString(error.messageRes())
            assertFalse(message.contains("Exception"))
            assertFalse(message.contains("at com."))
            assertFalse(message.contains("java."))
        }
    }

    /**
     * They are not the summary's sentences.
     *
     * That is the whole reason `AiChatError` is its own hierarchy: «no se ha podido generar el
     * resumen» is not «no se ha podido responder», and a shared enum would have made them the same
     * (011 research.md D-318).
     */
    @Test
    fun `the wording is about answering, never about summarising`() {
        val chat = everyError.map { context.getString(it.messageRes()).lowercase() }

        assertFalse(chat.any { it.contains("resumen") || it.contains("resumir") })
    }

    /** What is shown when the request had nothing to do with the document. Ours, not the model's. */
    @Test
    fun `the out-of-scope text says what can be asked instead of only refusing`() {
        val text = context.getString(com.jrblanco.boccantabria.R.string.ask_out_of_scope)

        assertTrue(text.isNotBlank())
        // A bare refusal is one sentence. Ours has a second one that offers a way forward, which is
        // the difference between «no» and «no, pero prueba esto». Asserted by shape rather than by
        // wording, so rewriting the sentence does not break the test — only shortening it to a
        // refusal would.
        val sentences = text.split('.').count { it.isNotBlank() }
        assertTrue("una negativa sin salida deja a la persona sin saber qué hacer", sentences >= 2)
    }
}
