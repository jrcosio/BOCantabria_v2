package com.jrblanco.boccantabria.data.source.remote

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** The response could not be understood as a bulletin feed at all. */
class BocRssParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Turns a source's XML into [RssChannelDto].
 *
 * Deliberately built on `javax.xml.parsers` and not on `XmlPullParser`: the pull parser lives in
 * `android.util.Xml`, which would force the thirty-odd cases the feed specification demands to
 * run under Robolectric. This way the parser is plain Kotlin and its tests are plain JUnit.
 *
 * Hardened in two layers, because the JVM and Android do not accept the same feature flags:
 *
 * 1. A **textual guard** that rejects a body carrying a document type or entity declaration.
 *    It is portable, it is the same on both platforms, and it is testable with a fixture.
 * 2. Best-effort flags on the factory, each inside a `runCatching`: a platform that does not know
 *    a flag must not bring the parser down.
 *
 * Parses by tag name, never by position, and ignores nodes it does not know — the source has
 * already added non-standard ones once.
 */
class BocRssParser {

    fun parse(body: String): RssChannelDto {
        rejectDangerousDeclarations(body)

        val document = try {
            secureFactory().newDocumentBuilder()
                .parse(ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
        } catch (cause: Exception) {
            throw BocRssParseException("the response is not well-formed XML", cause)
        }

        val channel = document.documentElement
            ?.childElements()
            ?.firstOrNull { it.tagName == TAG_CHANNEL }
            ?: throw BocRssParseException("no <channel> element in the response")

        val children = channel.childElements()
        return RssChannelDto(
            title = children.textOf(TAG_TITLE),
            link = children.textOf(TAG_LINK),
            description = children.textOf(TAG_DESCRIPTION),
            declaredSize = children.textOf(TAG_SIZE)?.toIntOrNull(),
            items = children
                .filter { it.tagName == TAG_ITEM }
                .take(MAX_ITEMS)
                .map(::toItem),
        )
    }

    private fun toItem(item: Element): RssItemDto {
        val fields = item.childElements()
        return RssItemDto(
            title = fields.textOf(TAG_TITLE),
            link = fields.textOf(TAG_LINK),
            pubDateRaw = fields.textOf(TAG_PUB_DATE),
            categoriesRaw = fields.textOf(TAG_CATEGORIES),
        )
    }

    /**
     * The portable half of the hardening. Android's parser silently ignores several of the
     * factory flags below, so the only guarantee that works everywhere is refusing the body
     * before a parser ever sees it.
     */
    private fun rejectDangerousDeclarations(body: String) {
        val prologue = body.take(PROLOGUE_SCAN_LIMIT).uppercase()
        if (prologue.contains("<!DOCTYPE") || prologue.contains("<!ENTITY")) {
            throw BocRssParseException("the response declares a document type or an entity")
        }
    }

    private fun secureFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            // Each flag on its own: the platforms do not support the same set, and an unknown
            // one throws. Failing to harden is acceptable here only because the textual guard
            // above already covers the attack these flags exist for.
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature(FEATURE_DISALLOW_DOCTYPE, true) }
            runCatching { setFeature(FEATURE_EXTERNAL_GENERAL_ENTITIES, false) }
            runCatching { setFeature(FEATURE_EXTERNAL_PARAMETER_ENTITIES, false) }
            runCatching { setFeature(FEATURE_LOAD_EXTERNAL_DTD, false) }
            runCatching { setAttribute(ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(ACCESS_EXTERNAL_SCHEMA, "") }
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            isNamespaceAware = false
        }

    private companion object {
        const val TAG_CHANNEL = "channel"
        const val TAG_ITEM = "item"
        const val TAG_TITLE = "title"
        const val TAG_LINK = "link"
        const val TAG_DESCRIPTION = "description"
        const val TAG_SIZE = "size"
        const val TAG_PUB_DATE = "pubDate"
        const val TAG_CATEGORIES = "categorias"

        const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
        const val FEATURE_EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities"
        const val FEATURE_EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities"
        const val FEATURE_LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"

        // Written out instead of taken from XMLConstants: those two fields are JAXP 1.5 and the
        // Android SDK does not declare them, so referencing them would not compile. The values
        // are the ones the specification fixes, and an unknown attribute is swallowed above.
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA =
            "http://javax.xml.XMLConstants/property/accessExternalSchema"

        /** Safety cap from the feed specification: no source should ever send more. */
        const val MAX_ITEMS = 500

        /** A declaration can only legally appear before the root element. */
        const val PROLOGUE_SCAN_LIMIT = 2_048
    }
}

private fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length)
        .map(nodes::item)
        .filterIsInstance<Element>()
}

/** First element with this tag name, trimmed; `null` when absent or blank. */
private fun List<Element>.textOf(tagName: String): String? = this
    .firstOrNull { it.tagName == tagName }
    ?.textContentOrNull()

private fun Node.textContentOrNull(): String? = textContent?.trim()?.takeIf { it.isNotEmpty() }
