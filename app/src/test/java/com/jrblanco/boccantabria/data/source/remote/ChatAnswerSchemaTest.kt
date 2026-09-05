package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.AiAnswerScope
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schema, and above all **the order of its properties**.
 *
 * Calqued from `SummarySchemaTest`, and here the stakes are higher. With constrained decoding the
 * declared order is the generation order, and anything after the long field is what disappears when a
 * generation is cut short — measured on the summary, where a grant call produced an empty card.
 *
 * Applied to an answer, that means `scope` must be **first**. It is the field that says whether the
 * answer belongs to the document, and a blank scope is not an empty card: it is the injection defence
 * falling over without a sound. Someone will eventually want to sort these alphabetically, which would
 * put `answer` in front. This is what stops them.
 */
class ChatAnswerSchemaTest {

    private val schema = ChatAnswerSchema.value.jsonObject
    private val properties = schema["properties"]!!.jsonObject
    private val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `the scope is generated first, before anything that could run long`() {
        assertEquals(
            "el ámbito debe ser la primera propiedad: si la generación se corta, es lo único que no " +
                "puede faltar",
            "scope",
            properties.keys.first(),
        )
    }

    @Test
    fun `the answer is generated last`() {
        assertEquals(
            "la respuesta debe ser la última propiedad del esquema",
            "answer",
            properties.keys.last(),
        )
    }

    @Test
    fun `the three properties are in the order the defence needs`() {
        assertEquals(listOf("scope", "sources", "answer"), properties.keys.toList())
    }

    @Test
    fun `the three are required, so none can simply be missing`() {
        assertEquals(listOf("scope", "sources", "answer"), required)
    }

    @Test
    fun `the scope is an enum of exactly the three domain values`() {
        val allowed = properties["scope"]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals(AiAnswerScope.entries.map { it.name }, allowed)
    }

    @Test
    fun `nothing outside the schema is allowed through`() {
        assertFalse(schema["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `the answer is bounded, because reaching the ceiling means the prompt is wrong`() {
        val maxLength = properties["answer"]!!.jsonObject["maxLength"]!!.jsonPrimitive.int()

        assertEquals(ChatAnswerSchema.MAX_ANSWER_LENGTH, maxLength)
    }

    @Test
    fun `the sources are bounded too`() {
        val maxItems = properties["sources"]!!.jsonObject["maxItems"]!!.jsonPrimitive.int()

        assertEquals(ChatAnswerSchema.MAX_SOURCES, maxItems)
    }

    @Test
    fun `a source carries a page and a label, and nothing else`() {
        val item = properties["sources"]!!.jsonObject["items"]!!.jsonObject
        val fields = item["properties"]!!.jsonObject.keys

        assertEquals(setOf("page", "label"), fields)
        assertFalse(item["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `the schema parses, which is the cheapest way to catch a stray comma`() {
        assertTrue(properties.isNotEmpty())
    }

    private fun kotlinx.serialization.json.JsonPrimitive.int(): Int = content.toInt()
}
