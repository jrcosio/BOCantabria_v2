package com.jrblanco.boccantabria.data.source.remote

import com.jrblanco.boccantabria.domain.model.EditionType
import com.jrblanco.boccantabria.domain.model.IdSource
import com.jrblanco.boccantabria.domain.model.ParserWarning
import com.jrblanco.boccantabria.domain.model.Publication
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Turns one raw announcement into a [Publication].
 *
 * Two rules govern everything here:
 *
 * - **The source decides the classification.** The `categorias` field is kept verbatim and used
 *   only to enrich and to cross-check. The 4.3 feed carries entries whose components are
 *   permuted, so anything that trusted their position would misfile them silently.
 * - **Untidy is not invalid.** Only three things reject an announcement: no title, an unusable
 *   link, or a date that cannot be read. Everything else is recorded as a warning and kept.
 */
class PublicationNormalizer {

    fun normalize(item: RssItemDto, definition: BocFeedDefinition): NormalizationResult {
        val title = item.title?.trim().orEmpty()
        if (title.isEmpty()) return NormalizationResult.Rejected(RejectionReason.BLANK_TITLE)

        val link = item.link?.trim().orEmpty()
        if (!link.startsWith(HTTPS_PREFIX) || link.length <= HTTPS_PREFIX.length) {
            return NormalizationResult.Rejected(RejectionReason.INVALID_LINK)
        }

        val date = parseDate(item.pubDateRaw)
            ?: return NormalizationResult.Rejected(RejectionReason.INVALID_DATE)

        val categories = analyseCategories(item.categoriesRaw, definition)
        val identity = identify(link, definition, date, title, item.categoriesRaw)

        return NormalizationResult.Accepted(
            Publication(
                externalKey = identity.externalKey,
                blobId = identity.blobId,
                idSource = identity.source,
                feedId = definition.feedId,
                sectionCode = definition.sectionCode,
                subsectionCode = definition.subsectionCode,
                title = title,
                issuer = categories.organizationPath.lastOrNull() ?: issuerFromTitle(title),
                organizationPath = categories.organizationPath,
                editionType = categories.editionType,
                publicationDate = date,
                documentUrl = link,
                rawCategories = item.categoriesRaw,
                warnings = categories.warnings,
            ),
        )
    }

    // ---------- Dates ----------

    /**
     * The source uses `yyyy-MM-dd`, not the RFC 822 form RSS normally carries. Anything else is
     * a rejection with a reason rather than a guess.
     */
    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // ---------- Categories ----------

    private data class CategoryAnalysis(
        val editionType: EditionType,
        val organizationPath: List<String>,
        val warnings: Set<ParserWarning>,
    )

    private fun analyseCategories(
        raw: String?,
        definition: BocFeedDefinition,
    ): CategoryAnalysis {
        val tokens = raw.orEmpty()
            .split(SEPARATOR)
            .map(String::trim)
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) {
            return CategoryAnalysis(
                editionType = EditionType.UNKNOWN,
                organizationPath = emptyList(),
                warnings = setOf(ParserWarning.CATEGORIES_ABSENT, ParserWarning.EDITION_TYPE_MISSING),
            )
        }

        val warnings = mutableSetOf<ParserWarning>()

        // The edition token is searched for by value, anywhere: in the 4.3 feed it turns up
        // first, last and in the middle.
        val editionIndex = tokens.indexOfFirst { EditionType.fromToken(it) != null }
        val editionType = tokens.getOrNull(editionIndex)?.let(EditionType::fromToken)
            ?: EditionType.UNKNOWN
        if (editionType == EditionType.UNKNOWN) warnings += ParserWarning.EDITION_TYPE_MISSING

        val classificationIndices = tokens.indices.filter { SECTION_PREFIX.containsMatchIn(tokens[it]) }
        val declaredCodes = classificationIndices
            .mapNotNull { SECTION_PREFIX.find(tokens[it])?.value?.trimEnd('.') }

        if (declaredCodes.isNotEmpty() && definition.classificationCode !in declaredCodes) {
            warnings += ParserWarning.CATEGORY_DOES_NOT_MATCH_FEED
        }
        if (isOrderUnreliable(tokens, editionIndex, classificationIndices)) {
            warnings += ParserWarning.CATEGORY_ORDER_UNRELIABLE
        }

        val organizationPath = tokens.filterIndexed { index, _ ->
            index != editionIndex && index !in classificationIndices
        }

        return CategoryAnalysis(editionType, organizationPath, warnings)
    }

    /**
     * The documented shape is: classification codes, then the organisation, then the edition
     * token last. Either deviation is worth recording, and neither is worth discarding content
     * over.
     */
    private fun isOrderUnreliable(
        tokens: List<String>,
        editionIndex: Int,
        classificationIndices: List<Int>,
    ): Boolean {
        val editionOutOfPlace = editionIndex >= 0 && editionIndex != tokens.lastIndex
        val classificationOutOfPlace = classificationIndices.isNotEmpty() &&
            classificationIndices != (0 until classificationIndices.size).toList()
        return editionOutOfPlace || classificationOutOfPlace
    }

    // ---------- Identity ----------

    private data class Identity(val externalKey: String, val blobId: String?, val source: IdSource)

    private fun identify(
        link: String,
        definition: BocFeedDefinition,
        date: LocalDate,
        title: String,
        rawCategories: String?,
    ): Identity {
        val blobId = BLOB_ID.find(link)?.groupValues?.get(1)
        if (blobId != null) {
            return Identity("$KEY_PREFIX_BLOB$blobId", blobId, IdSource.BLOB_ID)
        }

        // The canonical URL is only an identifier if it actually distinguishes one announcement.
        // A bare endpoint with no query is the same string for every announcement that reaches
        // it, so using it as a key would collapse them all into one row.
        if (link.substringAfter('?', "").isNotEmpty()) {
            return Identity(link, null, IdSource.CANONICAL_URL)
        }

        val digest = sha256("${definition.feedId}|$date|$title|${rawCategories.orEmpty()}")
        return Identity("$KEY_PREFIX_HASH$digest", null, IdSource.CONTENT_HASH)
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    /**
     * Many titles read `ORGANISMO: descripción`. Auxiliary only: it is used when `categorias`
     * left no organisation path, and never to override one.
     */
    private fun issuerFromTitle(title: String): String? = title
        .substringBefore(':', "")
        .trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_ISSUER_LENGTH }

    private companion object {
        const val HTTPS_PREFIX = "https://"
        const val SEPARATOR = '|'
        const val KEY_PREFIX_BLOB = "boc:"
        const val KEY_PREFIX_HASH = "hash:"
        const val MAX_ISSUER_LENGTH = 120

        val BLOB_ID = Regex("""[?&]idAnuBlob=(\d+)""")

        /** `1.`, `4.3.`… the shape the specification gives for a classification component. */
        val SECTION_PREFIX = Regex("""^\d+(?:\.\d+)?\.""")
    }
}
