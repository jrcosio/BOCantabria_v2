package com.jrblanco.boccantabria.data.source.local

import com.jrblanco.boccantabria.core.util.SearchText

/**
 * Everything of a publication that a search can match, in one normalised string.
 *
 * Two places write `search_text` —the synchronisation, as announcements arrive, and the backfill,
 * for the rows that predate the column— and both come through here so the two can never drift.
 *
 * The section and subsection **names** are part of it on purpose: the table only stores codes, so
 * without them typing `contratacion` would find nothing. They come from the compiled catalogue,
 * which the repository already has injected.
 *
 * Deliberately absent: `raw_categories`, which the service ships verbatim and with the components
 * permuted in feed 4.3, and the issuer-stripped title, which is derived from [title] and would only
 * double the column's size.
 */
internal fun buildSearchText(
    title: String,
    issuer: String?,
    organizationPath: List<String>,
    blobId: String?,
    sectionName: String?,
    subsectionName: String?,
): String = SearchText.normalise(
    buildList {
        add(title)
        issuer?.let(::add)
        addAll(organizationPath)
        blobId?.let(::add)
        sectionName?.let(::add)
        subsectionName?.let(::add)
    }.joinToString(separator = " "),
)
