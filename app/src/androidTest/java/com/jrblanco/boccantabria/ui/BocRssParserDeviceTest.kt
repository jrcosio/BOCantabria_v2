package com.jrblanco.boccantabria.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.jrblanco.boccantabria.data.source.remote.BocRssParseException
import com.jrblanco.boccantabria.data.source.remote.BocRssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A smoke test on a real device.
 *
 * The parser is built on `javax.xml.parsers` so its thirty-odd cases can run without an emulator,
 * and Android's implementation does not accept the same hardening flags as the JVM's. Without
 * this test, that decision would rest on an assumption; with it, the assumption is checked where
 * the application actually runs.
 */
class BocRssParserDeviceTest {

    private val parser = BocRssParser()

    @Test
    fun a_real_feed_parses_on_a_device_too() {
        val channel = parser.parse(FEED)

        assertEquals("Filtro BOC", channel.title)
        assertEquals(1, channel.items.size)
        assertEquals("2026-08-26", channel.items.single().pubDateRaw)
        assertTrue(channel.items.single().title!!.contains("PIÉLAGOS"))
    }

    @Test
    fun a_document_type_declaration_is_refused_on_a_device_too() {
        // This is the case that could only be covered by the textual guard: Android silently
        // ignores several of the factory features the JVM honours.
        assertThrows(BocRssParseException::class.java) {
            parser.parse(
                "<?xml version=\"1.0\"?><!DOCTYPE rss [ <!ENTITY x SYSTEM \"file:///etc/hosts\"> ]>" +
                    "<rss version=\"2.0\"><channel><title>Filtro BOC</title></channel></rss>",
            )
        }
    }

    @Test
    fun the_instrumentation_really_runs_on_android() {
        assertTrue(InstrumentationRegistry.getInstrumentation().targetContext.packageName.isNotEmpty())
    }

    private companion object {
        val FEED = """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0"><channel>
              <title>Filtro BOC</title>
              <link>https://www.cantabria.es/o/BOC/feed/6802081</link>
              <description>Contenidos del BOC por categorias: 1.Disposiciones Generales</description>
              <size>1</size>
              <item>
                <title>AYUNTAMIENTO DE PIÉLAGOS: Aprobación definitiva de la Ordenanza Fiscal.</title>
                <link>https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765</link>
                <pubDate>2026-08-26</pubDate>
                <categorias>1.Disposiciones Generales|Ayuntamiento de Piélagos|ORD</categorias>
              </item>
            </channel></rss>
        """.trimIndent()
    }
}
