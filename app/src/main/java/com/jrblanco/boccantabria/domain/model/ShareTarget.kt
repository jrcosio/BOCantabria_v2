package com.jrblanco.boccantabria.domain.model

/**
 * What sharing ended up offering, and why.
 *
 * Sharing sends the document. When it cannot, it offers the link instead — and the screen has to be
 * able to **say why**, so the person is not surprised by getting something different from what they
 * asked for. A boolean could not have carried the reason.
 */
sealed interface ShareTarget {

    data class Document(val document: OfficialDocument) : ShareTarget

    data class Link(val url: String, val reason: LinkReason) : ShareTarget {
        init {
            require(url.startsWith("https://")) { "url must be https, was: $url" }
        }
    }

    /**
     * Why the link was offered instead of the document.
     *
     * Nested inside [ShareTarget] because it has no meaning apart from it — a reason with nothing
     * to give a reason for is not a concept.
     */
    enum class LinkReason {
        /** The document was not stored and there was no way to fetch it. */
        NO_CONNECTION,
    }
}
