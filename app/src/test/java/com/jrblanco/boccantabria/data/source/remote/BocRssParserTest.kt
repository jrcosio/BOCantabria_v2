package com.jrblanco.boccantabria.data.source.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matrix the feed specification demands, run against samples taken from the live service —
 * including the 4.3 feed with its permuted components and the 8.1 feed that is legitimately
 * empty.
 *
 * Plain JUnit, no emulator and no Robolectric: that is the whole reason the parser is built on
 * `javax.xml.parsers` instead of on the platform's pull parser.
 */
class BocRssParserTest {

    private val parser = BocRssParser()

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("fixtures/$name")?.bufferedReader()?.readText(),
    ) { "missing fixture: $name" }

    // ---------- Channel ----------

    @Test
    fun `a valid channel is read whole`() {
        val channel = parser.parse(fixture("feed_1_disposiciones.xml"))

        assertEquals("Filtro BOC", channel.title)
        assertEquals("https://www.cantabria.es/o/BOC/feed/6802081", channel.link)
        assertEquals(100, channel.declaredSize)
        assertEquals(5, channel.items.size)
    }

    @Test
    fun `a channel with zero items is a valid response, not a failure`() {
        val channel = parser.parse(fixture("feed_8_1_vacio.xml"))

        assertEquals(0, channel.declaredSize)
        assertTrue(channel.items.isEmpty())
    }

    @Test
    fun `the declared size is diagnostic, so a mismatch does not reject the response`() {
        val channel = parser.parse(fixture("feed_size_incorrecto.xml"))

        assertEquals(100, channel.declaredSize)
        assertEquals(2, channel.items.size)
    }

    @Test
    fun `a non numeric size is read as absent rather than throwing`() {
        val channel = parser.parse(fixture("feed_campos_desconocidos.xml"))

        assertNull(channel.declaredSize)
        assertEquals(3, channel.items.size)
    }

    @Test
    fun `an absent size is absent, and the items are still read`() {
        val channel = parser.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <rss version="2.0"><channel>
              <title>Filtro BOC</title>
              <item><title>Uno</title><link>https://boc.cantabria.es/a?idAnuBlob=1</link>
                <pubDate>2026-08-27</pubDate></item>
            </channel></rss>
            """.trimIndent(),
        )

        assertNull(channel.declaredSize)
        assertEquals(1, channel.items.size)
    }

    @Test
    fun `unknown nodes are ignored instead of breaking the read`() {
        val channel = parser.parse(fixture("feed_campos_desconocidos.xml"))

        assertEquals("7.Otros Anuncios", channel.items.first().categoriesRaw?.substringBefore('|'))
    }

    // ---------- Items ----------

    @Test
    fun `the four documented fields are read`() {
        val item = parser.parse(fixture("feed_1_disposiciones.xml")).items.first()

        assertTrue(item.title!!.startsWith("AYUNTAMIENTO DE CAMPOO DE ENMEDIO:"))
        assertEquals(
            "https://boc.cantabria.es/boces/verAnuncioAction.do?idAnuBlob=439765",
            item.link,
        )
        assertEquals("2026-08-26", item.pubDateRaw)
        assertEquals(
            "1.Disposiciones Generales|Ayuntamiento de Campoo de Enmedio|ORD",
            item.categoriesRaw,
        )
    }

    @Test
    fun `fields in a different order are read the same, because nothing depends on position`() {
        val item = parser.parse(fixture("feed_campos_desconocidos.xml")).items.first()

        assertEquals("2026-08-27", item.pubDateRaw)
        assertNotNull(item.title)
        assertNotNull(item.link)
        assertNotNull(item.categoriesRaw)
    }

    @Test
    fun `a very long title is read whole, without truncating`() {
        val title = parser.parse(fixture("feed_campos_desconocidos.xml")).items.first().title!!

        assertTrue("expected a long title, got ${title.length} characters", title.length > 300)
    }

    @Test
    fun `special characters and entities survive`() {
        val title = parser.parse(fixture("feed_campos_desconocidos.xml")).items[1].title!!

        assertTrue(title.contains("Aprobación & publicación"))
        assertTrue(title.contains("«Plan General»"))
        assertTrue(title.contains("2.ª fase"))
    }

    @Test
    fun `an item without categories keeps the rest of its fields`() {
        val item = parser.parse(fixture("feed_item_sin_categorias.xml")).items.first()

        assertNull(item.categoriesRaw)
        assertNotNull(item.title)
        assertEquals("2026-08-27", item.pubDateRaw)
    }

    @Test
    fun `an unparseable date reaches the caller as text, to be rejected with a reason`() {
        val item = parser.parse(fixture("feed_fecha_invalida.xml")).items.first()

        assertEquals("26/08/2026", item.pubDateRaw)
    }

    @Test
    fun `a link without an identifier is still read`() {
        val item = parser.parse(fixture("feed_fecha_invalida.xml")).items[1]

        assertEquals("https://boc.cantabria.es/boces/verAnuncioAction.do", item.link)
    }

    @Test
    fun `a blank title is read as absent`() {
        val item = parser.parse(fixture("feed_fecha_invalida.xml")).items[3]

        assertNull(item.title)
    }

    @Test
    fun `the permuted categories of the 4_3 feed do not break the read`() {
        val channel = parser.parse(fixture("feed_4_3_anomalo.xml"))

        assertEquals(9, channel.items.size)
        // Second and third entries carry the components in two different wrong orders. Both are
        // read; sorting them out is the normaliser's job, not the parser's.
        assertTrue(channel.items[1].categoriesRaw!!.startsWith("Ayuntamiento de Miengo|ORD|"))
        assertTrue(channel.items[2].categoriesRaw!!.startsWith("ORD|4.3."))
    }

    @Test
    fun `every announcement of the 4_3 feed survives, because none may be discarded`() {
        val channel = parser.parse(fixture("feed_4_3_anomalo.xml"))

        assertEquals(9, channel.items.count { it.title != null && it.link != null })
    }

    // ---------- Security ----------

    @Test
    fun `a body declaring a document type is refused`() {
        val error = assertThrows(BocRssParseException::class.java) {
            parser.parse(fixture("feed_con_doctype.xml"))
        }

        assertTrue(error.message!!.contains("document type"))
    }

    @Test
    fun `a body declaring an external entity is refused`() {
        assertThrows(BocRssParseException::class.java) {
            parser.parse(fixture("feed_con_entidad_externa.xml"))
        }
    }

    @Test
    fun `the refusal happens before parsing, so it does not depend on platform flags`() {
        // Android ignores several of the factory features the JVM honours. This shape must be
        // refused by the textual guard alone, on any platform.
        val error = assertThrows(BocRssParseException::class.java) {
            parser.parse(
                "<?xml version=\"1.0\"?>\n<!doctype rss [ <!ENTITY x SYSTEM \"file:///etc/passwd\"> ]>" +
                    "<rss><channel><title>t</title></channel></rss>",
            )
        }

        assertTrue(error.message!!.contains("document type or an entity"))
    }

    // ---------- Malformed input ----------

    @Test
    fun `text that is not XML is refused with a clear cause`() {
        assertThrows(BocRssParseException::class.java) { parser.parse("<html><body>502</body></html>") }
        assertThrows(BocRssParseException::class.java) { parser.parse("") }
        assertThrows(BocRssParseException::class.java) { parser.parse("{\"error\":true}") }
    }

    @Test
    fun `XML without a channel is refused`() {
        val error = assertThrows(BocRssParseException::class.java) {
            parser.parse("<?xml version=\"1.0\"?><rss version=\"2.0\"></rss>")
        }

        assertTrue(error.message!!.contains("channel"))
    }
}
