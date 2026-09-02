package com.jrblanco.boccantabria.data.source.remote

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strict schema, and above all **the order of its properties**.
 *
 * That order is not cosmetic: with constrained decoding it is the order in which the model generates
 * the fields. The first real answers proved it the hard way — `plainLanguageSummary` sat fourth, ran
 * to exactly 1024 characters, got cut mid-word, and every list after it came back **empty**. A grant
 * call with deadlines and amounts produced a card with nothing in it.
 *
 * So the prose goes **last**: whatever happens to it, the structured half is already written. Someone
 * will eventually want to sort these alphabetically, and this is what will stop them.
 */
class GroqSummarySchemaTest {

    private val schema = GroqSummarySchema.value.jsonObject["json_schema"]!!.jsonObject
    private val properties = schema["schema"]!!.jsonObject["properties"]!!.jsonObject
    private val required = schema["schema"]!!.jsonObject["required"]!!.jsonArray
        .map { it.jsonPrimitive.content }

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

        listOf(
            "keyPoints", "affectedParties", "datesAndDeadlines",
            "amounts", "requiredActions", "appealsOrClaims",
        ).forEach { field ->
            assertTrue("«$field» debe generarse antes que la prosa", order.indexOf(field) < prose)
        }
    }

    /** Coverage is what lets the screen tell the truth about a partial summary. It goes early too. */
    @Test
    fun `coverage is generated before the prose`() {
        val order = properties.keys.toList()

        assertTrue(order.indexOf("coverage") < order.indexOf("plainLanguageSummary"))
    }

    /** Strict mode demands every property in `required`, and `required` mirrors the same order. */
    @Test
    fun `required lists every property, in the same order`() {
        assertEquals(properties.keys.toList(), required)
    }

    @Test
    fun `the schema is strict and closed`() {
        assertEquals("boc_ai_summary", schema["name"]!!.jsonPrimitive.content)
        assertTrue(schema["strict"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            false,
            schema["schema"]!!.jsonObject["additionalProperties"]!!.jsonPrimitive.content.toBoolean(),
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
}
