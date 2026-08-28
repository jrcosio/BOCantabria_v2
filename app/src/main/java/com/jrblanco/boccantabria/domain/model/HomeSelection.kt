package com.jrblanco.boccantabria.domain.model

/**
 * What the home screen is showing.
 *
 * The two cases are not variations of one query: the day's bulletin spans every section but a
 * single date, while a section spans every date. Keeping them apart is what stops sections with
 * no recent activity —4.3 has published nothing since 2021, 8.1 is empty— from always looking
 * broken.
 */
sealed interface HomeSelection {

    /** Everything published on the most recent date available. */
    data object TodaysBulletin : HomeSelection

    /** One section or subsection, with no date restriction. */
    data class Section(
        val sectionCode: String,
        val subsectionCode: String? = null,
    ) : HomeSelection {
        init {
            require(sectionCode.isNotBlank()) { "sectionCode must not be blank" }
            require(subsectionCode == null || subsectionCode.startsWith("$sectionCode.")) {
                "subsection '$subsectionCode' does not belong to section '$sectionCode'"
            }
        }

        /** The most specific code chosen. */
        val code: String get() = subsectionCode ?: sectionCode
    }

    companion object {

        /** Rebuilds a selection from navigation arguments. Both null means the day's bulletin. */
        fun of(sectionCode: String?, subsectionCode: String?): HomeSelection =
            if (sectionCode.isNullOrBlank()) {
                TodaysBulletin
            } else {
                Section(sectionCode, subsectionCode?.takeIf { it.isNotBlank() })
            }
    }
}
