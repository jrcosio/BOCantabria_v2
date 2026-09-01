package com.jrblanco.boccantabria.core.util

import java.text.Normalizer
import java.util.Locale

/**
 * The shape a piece of text takes before anything is compared against it.
 *
 * SQLite's `LIKE` folds case only for ASCII and **never** folds diacritics, and Android ships no
 * ICU collation that would. So the folding happens here, in Kotlin, and it happens **on the way
 * in**: every publication stores its searchable text already normalised, and every query is
 * normalised the same way before it is compared. Doing it once per announcement is also cheaper
 * than doing it once per row per keystroke.
 *
 * Used by all three layers — `data` writes the column with it, `domain` filters the bulletin in
 * memory with it — which is why it lives in `core/util`, beside the other cross-cutting providers.
 *
 * **If this function ever changes, the stored column stops agreeing with the queries.** Anything
 * already written would have to be rebuilt; there is no version marker that would notice on its own.
 */
object SearchText {

    /**
     * Lower-cased, stripped of diacritics, with runs of whitespace collapsed.
     *
     * `Locale.ROOT` is deliberate. The column is written on one day and queried on another, and the
     * phone's language can change in between; in Turkish, `lowercase()` turns `I` into a dotless
     * `ı`, and the two runs would silently disagree.
     *
     * The tilde of `ñ` is a diacritic and goes with the rest: `España` is stored as `espana`. That
     * is the point — `espana` and `España` normalise to the same thing and find each other.
     */
    fun normalise(raw: String): String = Normalizer
        .normalize(raw, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE_RUN, " ")
        .trim()

    /** Everything Unicode classifies as a non-spacing mark: what NFD leaves behind. */
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    private val WHITESPACE_RUN = Regex("\\s+")
}
