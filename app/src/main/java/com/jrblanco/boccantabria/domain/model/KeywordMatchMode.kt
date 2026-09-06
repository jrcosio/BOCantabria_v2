package com.jrblanco.boccantabria.domain.model

/**
 * How the keywords of an alert rule combine.
 *
 * [ANY] is the default of the form: it is the easier one to understand and casts the wider net.
 * [ALL] narrows: every word has to appear, in any order and anywhere in the text.
 */
enum class KeywordMatchMode {
    ANY,
    ALL,
    ;

    companion object {
        /**
         * Tolerant restoration from a stored name.
         *
         * An unknown or missing name becomes [ANY] rather than throwing: a value written by another
         * version of the application must never take the alerts screen down (same lesson as the
         * detail tab, feature 007).
         */
        fun byNameOrDefault(name: String?): KeywordMatchMode =
            entries.firstOrNull { it.name == name } ?: ANY
    }
}
