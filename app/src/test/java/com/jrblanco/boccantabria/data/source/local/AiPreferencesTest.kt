package com.jrblanco.boccantabria.data.source.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jrblanco.boccantabria.di.ROBOLECTRIC_SDK
import com.jrblanco.boccantabria.fake.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK], application = Application::class)
class AiPreferencesTest {

    private val dispatcher = StandardTestDispatcher()

    /** FR-043: a fresh installation has not been told anything yet. */
    @Test
    fun `a fresh installation has not accepted the notice`() = runTest(dispatcher) {
        assertFalse(preferences().observeNoticeAccepted().first())
    }

    @Test
    fun `accepting is remembered`() = runTest(dispatcher) {
        val preferences = preferences()

        preferences.acceptNotice()

        assertTrue(preferences.observeNoticeAccepted().first())
    }

    /**
     * FR-045: shown once and never again. Surviving a new instance is the point — the flag has to
     * outlive the object that wrote it, or the sheet would come back on the next launch.
     */
    @Test
    fun `the acceptance survives a new instance over the same store`() = runTest(dispatcher) {
        preferences().acceptNotice()

        assertTrue(preferences().observeNoticeAccepted().first())
    }

    /**
     * **Regresión, 009 FR-031a y D-113.** El aviso amplía su texto —ahora dice que el servicio puede
     * usar el texto de ese documento público para mejorar sus modelos— y quien ya lo había aceptado
     * nunca leyó esa frase. La clave se versiona para que vuelva a verlo **una sola vez**.
     *
     * Esta prueba escribe la clave antigua a mano y exige que no se lea. Falla antes del cambio, que
     * es exactamente para lo que está: sin ella, versionar la clave sería un cambio sin comprobar.
     */
    @Test
    fun `an acceptance stored under the previous key is not read`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("boc_ai", Application.MODE_PRIVATE)
            .edit()
            .putBoolean("ai_notice_accepted", true)
            .commit()

        assertFalse(
            "el aviso debe volver a mostrarse una vez, porque su texto cambió",
            preferences().observeNoticeAccepted().first(),
        )
    }

    /**
     * **Regresión, 010 FR-032 y FR-033.** Y esta vez el cambio es mayor que una frase: lo que sale
     * del dispositivo deja de ser el **texto** que extraíamos y pasa a ser el **documento**, que el
     * servicio conserva un tiempo. Aceptar «se envía el texto» no era aceptar eso.
     *
     * Se comprueba con la clave de la 009, que es la que tiene instalada quien viene de la versión
     * anterior. La de la 007 sigue cubierta por la prueba de arriba: ninguna de las dos se lee.
     */
    @Test
    fun `an acceptance stored under the feature 009 key is not read either`() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("boc_ai", Application.MODE_PRIVATE)
            .edit()
            .putBoolean("ai_notice_accepted_v2", true)
            .commit()

        assertFalse(
            "el aviso debe volver a mostrarse una vez: ahora viaja el documento, no su texto",
            preferences().observeNoticeAccepted().first(),
        )
    }

    private fun preferences() = aiPreferences(
        context = ApplicationProvider.getApplicationContext<Application>(),
        dispatchers = TestDispatcherProvider(dispatcher),
    )
}
