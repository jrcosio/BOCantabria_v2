package com.jrblanco.boccantabria.data.source.local

/**
 * Turns already-normalised text into a `LIKE` pattern that matches it anywhere.
 *
 * Lives here, next to the SQL, and not in `core/util` with the normalisation: escaping is a rule of
 * SQL, not of Spanish. The bulletin's in-place search compares strings in memory and must not drag
 * backslashes into what it is comparing.
 *
 * Every statement that binds the result **must** end in `ESCAPE '\'`.
 */
internal fun likeContains(normalisedQuery: String): String {
    val escaped = normalisedQuery
        // The escape character first: doing it later would escape the backslashes this very
        // function had just introduced.
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
