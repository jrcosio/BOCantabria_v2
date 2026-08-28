package com.jrblanco.boccantabria.domain.model

import java.time.LocalDate

/**
 * What the editorial header shows for the current selection.
 *
 * There is no bulletin number here on purpose: the official feeds do not publish one, and
 * fabricating it would present invented data as official. The outlined badge carries
 * [publicationCount] instead.
 *
 * @param sectionName `null` when the selection is the day's bulletin.
 * @param date the date of the day's bulletin, or the most recent one within the section.
 */
data class BulletinHeaderData(
    val date: LocalDate?,
    val publicationCount: Int,
    val sectionName: String? = null,
) {
    init {
        require(publicationCount >= 0) { "publicationCount must not be negative" }
    }

    val isTodaysBulletin: Boolean get() = sectionName == null

    companion object {
        val EMPTY = BulletinHeaderData(date = null, publicationCount = 0)
    }
}
