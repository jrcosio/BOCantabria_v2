package com.jrblanco.boccantabria.domain.model

/**
 * Whether a publication belongs to an ordinary or an extraordinary edition of the bulletin.
 *
 * The source encodes it as a token inside the `categorias` field, but **not always in the last
 * position**: the 4.3 feed carries old entries with the components permuted. Callers must look
 * for the token anywhere, which is why [fromToken] takes a single token and the search stays
 * with the caller.
 */
enum class EditionType {
    ORDINARY,
    EXTRAORDINARY,

    /** The token was absent. A publication is never rejected for this. */
    UNKNOWN,
    ;

    companion object {

        /** Returns the type a token encodes, or `null` when the token is something else. */
        fun fromToken(token: String): EditionType? = when (token.trim().uppercase()) {
            "ORD" -> ORDINARY
            "EXT" -> EXTRAORDINARY
            else -> null
        }
    }
}
