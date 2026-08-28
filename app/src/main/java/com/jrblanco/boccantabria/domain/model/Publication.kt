package com.jrblanco.boccantabria.domain.model

import java.time.LocalDate

/**
 * One announcement of the Boletín Oficial de Cantabria.
 *
 * Its classification comes from the **feed it was obtained from**, never from the `categorias`
 * field the source ships with it: that field carries permuted components in old entries, so
 * trusting it would silently misfile publications. `categorias` is kept verbatim in
 * [rawCategories] so the original is always recoverable.
 *
 * @param externalKey stable identity across syncs. See [IdSource] for how it is derived.
 * @param blobId the source's own identifier when the link carried one.
 * @param title kept whole, exactly as received. Truncation is a screen concern.
 * @param organizationPath issuer hierarchy, from broadest to narrowest.
 * @param warnings anomalies found while normalising. Never a reason to drop the publication.
 */
data class Publication(
    val externalKey: String,
    val blobId: String?,
    val idSource: IdSource,
    val feedId: String,
    val sectionCode: String,
    val subsectionCode: String?,
    val title: String,
    val issuer: String?,
    val organizationPath: List<String>,
    val editionType: EditionType,
    val publicationDate: LocalDate,
    val documentUrl: String,
    val rawCategories: String?,
    val warnings: Set<ParserWarning> = emptySet(),
) {
    init {
        require(externalKey.isNotBlank()) { "externalKey must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(feedId.isNotBlank()) { "feedId must not be blank" }
        require(sectionCode.isNotBlank()) { "sectionCode must not be blank" }
        require(documentUrl.startsWith(HTTPS_PREFIX)) {
            "documentUrl must be https, was: $documentUrl"
        }
        require(organizationPath.none { it.isBlank() }) {
            "organizationPath must not contain blank elements"
        }
    }

    /** The most specific classification available: the subsection when there is one. */
    val classificationCode: String get() = subsectionCode ?: sectionCode

    private companion object {
        const val HTTPS_PREFIX = "https://"
    }
}
