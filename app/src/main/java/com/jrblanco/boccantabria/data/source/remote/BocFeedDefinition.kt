package com.jrblanco.boccantabria.data.source.remote

/**
 * One of the nineteen official sources, and the classification it speaks for.
 *
 * The section a source represents is **authoritative by construction**: feed 6802097 *is*
 * Urbanismo, always, whatever the `categorias` field of an individual announcement happens to
 * say. That is what makes the classification reliable despite a decade of untidy entries.
 *
 * @param enabled a source can be switched off without touching the reader, as the feed
 *   specification requires: if one of them starts returning rubbish, it is turned off and the
 *   other eighteen keep working.
 */
data class BocFeedDefinition(
    val feedId: String,
    val url: String,
    val sectionCode: String,
    val subsectionCode: String?,
    val order: Int,
    val enabled: Boolean = true,
) {
    init {
        require(feedId.isNotBlank()) { "feedId must not be blank" }
        require(url.startsWith("https://")) { "feed url must be https, was: $url" }
        require(sectionCode.isNotBlank()) { "sectionCode must not be blank" }
        require(subsectionCode == null || subsectionCode.startsWith("$sectionCode.")) {
            "subsection '$subsectionCode' does not belong to section '$sectionCode'"
        }
    }

    /** The most specific classification this source speaks for. */
    val classificationCode: String get() = subsectionCode ?: sectionCode
}
