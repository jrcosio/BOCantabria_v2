package com.jrblanco.boccantabria.domain.model

/**
 * What the in-app message says when the application is on screen and a cycle found something.
 *
 * @param ruleName only when a single publication matched a single rule: «Una nueva publicación
 *   coincide con «Ganadería»». Otherwise the message counts publications.
 */
data class InAppAlert(
    val publicationCount: Int,
    val ruleName: String?,
) {
    init {
        require(publicationCount >= 1) { "an in-app alert is about at least one publication" }
    }

    /** Two alerts pending at once become one that counts both. */
    operator fun plus(other: InAppAlert): InAppAlert = InAppAlert(
        publicationCount = publicationCount + other.publicationCount,
        ruleName = null,
    )
}
