package com.jrblanco.boccantabria.domain.model

/**
 * Anomalies found while normalising a publication.
 *
 * They are recorded, never acted upon: none of them rejects a publication. The source is a
 * public service with no availability commitment and a decade of accumulated inconsistencies;
 * dropping content because it is untidy would lose legitimate announcements.
 */
enum class ParserWarning {

    /** The section declared in `categorias` is not the one the feed represents. The feed wins. */
    CATEGORY_DOES_NOT_MATCH_FEED,

    /** Neither `ORD` nor `EXT` appeared in any component. */
    EDITION_TYPE_MISSING,

    /** The components were not in the documented order. Known to happen in the 4.3 feed. */
    CATEGORY_ORDER_UNRELIABLE,

    /** The publication carried no `categorias` field at all. */
    CATEGORIES_ABSENT,
}
