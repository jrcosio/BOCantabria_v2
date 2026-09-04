package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schema, and above all **the order of its properties**.
 *
 * That order is not cosmetic: with constrained decoding it is the order in which the model generates
 * the fields. The first real answers proved it the hard way — `plainLanguageSummary` sat fourth, ran
 * to exactly 1024 characters, got cut mid-word, and every list after it came back **empty**. A grant
 * call with deadlines and amounts produced a card with nothing in it.
 *
 * So the prose goes **last**: whatever happens to it, the structured half is already written. Someone
 * will eventually want to sort these alphabetically, and this is what will stop them.
 *
 * Feature 009 changed the provider and this test survived almost unchanged, which is the point: the
 * OpenAI-style envelope went away, the schema object did not. What is new is the cap per section
 * (009 FR-007).
 */
class SummarySchemaTest {

    private val schema = SummarySchema.value.jsonObject
    private val properties = schema["properties"]!!.jsonObject
    private val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

    private val referencedLists = listOf(
        "keyPoints", "affectedParties", "datesAndDeadlines",
        "amounts", "requiredActions", "appealsOrClaims",
    )

    @Test
    fun `the prose is generated last, after every structured field`() {
        assertEquals(
            "el resumen llano debe ser la última propiedad del esquema",
            "plainLanguageSummary",
            properties.keys.last(),
        )
    }

    @Test
    fun `the six structured lists come before the prose`() {
        val order = properties.keys.toList()
        val prose = order.indexOf("plainLanguageSummary")

        referencedLists.forEach { field ->
            assertTrue("«$field» debe generarse antes que la prosa", order.indexOf(field) < prose)
        }
    }

    /** Coverage is what lets the screen tell the truth about a partial summary. It goes early too. */
    @Test
    fun `coverage is generated before the prose`() {
        val order = properties.keys.toList()

        assertTrue(order.indexOf("coverage") < order.indexOf("plainLanguageSummary"))
    }

    @Test
    fun `required lists every property, in the same order`() {
        assertEquals(properties.keys.toList(), required)
    }

    /**
     * No envelope any more.
     *
     * The previous provider wrapped this in `{"type":"json_schema","json_schema":{…}}` with a name
     * and a `strict` flag. This one takes the schema object itself, so the top level must be the
     * object — if someone reinstates the wrapper, every request comes back a 400.
     */
    @Test
    fun `the schema is the object itself, with no provider envelope around it`() {
        assertEquals("object", schema["type"]!!.jsonPrimitive.content)
        assertNull(schema["json_schema"])
        assertNull(schema["strict"])
    }

    @Test
    fun `the schema is closed`() {
        assertEquals(
            false,
            schema["additionalProperties"]!!.jsonPrimitive.content.toBoolean(),
        )
    }

    /**
     * The prose is bounded so the model plans a summary that fits, instead of writing until something
     * cuts it. Nine hundred characters is roughly the 150 words the prompt asks for.
     */
    @Test
    fun `the prose declares a maximum length`() {
        val prose = properties["plainLanguageSummary"]!!.jsonObject

        assertEquals(900, prose["maxLength"]!!.jsonPrimitive.content.toInt())
    }

    /**
     * 009 FR-007. A problem the feature created: until the whole document went in, no card could
     * grow much.
     */
    @Test
    fun `each referenced list is capped at ten items`() {
        referencedLists.forEach { field ->
            val list = properties[field]!!.jsonObject

            assertEquals(
                "«$field» debe llevar tope de diez",
                SummaryValidator.MAX_ITEMS_PER_SECTION,
                list["maxItems"]!!.jsonPrimitive.content.toInt(),
            )
        }
    }

    /**
     * `warnings` is deliberately uncapped: it is where the notice about a capped section travels, and
     * bounding it could truncate the very explanation of a truncation.
     */
    @Test
    fun `warnings carries no cap`() {
        assertNull(properties["warnings"]!!.jsonObject["maxItems"])
    }
}
