package com.jrblanco.boccantabria.data.source.remote

/**
 * The nineteen official sources of the bulletin.
 *
 * Every address is **written out in full**. Composing them by appending a number to a base would
 * be shorter and would be a mistake: the identifiers are not a sequence —they run from 6802081
 * to 7479572 with gaps— and a computed address that happens to resolve would silently file
 * announcements under the wrong section.
 *
 * This lives in `data` and not in `domain` because an address is provenance, not business
 * knowledge. The day a service of our own exists, the sections stay and this file goes.
 */
object BocFeedCatalog {

    val definitions: List<BocFeedDefinition> = listOf(
        BocFeedDefinition(
            feedId = "6802081",
            url = "https://www.cantabria.es/o/BOC/feed/6802081",
            sectionCode = "1", subsectionCode = null, order = 1,
        ),
        BocFeedDefinition(
            feedId = "6802084",
            url = "https://www.cantabria.es/o/BOC/feed/6802084",
            sectionCode = "2", subsectionCode = "2.1", order = 2,
        ),
        BocFeedDefinition(
            feedId = "6802085",
            url = "https://www.cantabria.es/o/BOC/feed/6802085",
            sectionCode = "2", subsectionCode = "2.2", order = 3,
        ),
        BocFeedDefinition(
            feedId = "6802086",
            url = "https://www.cantabria.es/o/BOC/feed/6802086",
            sectionCode = "2", subsectionCode = "2.3", order = 4,
        ),
        BocFeedDefinition(
            feedId = "6802087",
            url = "https://www.cantabria.es/o/BOC/feed/6802087",
            sectionCode = "3", subsectionCode = null, order = 5,
        ),
        BocFeedDefinition(
            feedId = "6802089",
            url = "https://www.cantabria.es/o/BOC/feed/6802089",
            sectionCode = "4", subsectionCode = "4.1", order = 6,
        ),
        BocFeedDefinition(
            feedId = "6802090",
            url = "https://www.cantabria.es/o/BOC/feed/6802090",
            sectionCode = "4", subsectionCode = "4.2", order = 7,
        ),
        BocFeedDefinition(
            feedId = "6802091",
            url = "https://www.cantabria.es/o/BOC/feed/6802091",
            sectionCode = "4", subsectionCode = "4.3", order = 8,
        ),
        BocFeedDefinition(
            feedId = "6802092",
            url = "https://www.cantabria.es/o/BOC/feed/6802092",
            sectionCode = "4", subsectionCode = "4.4", order = 9,
        ),
        BocFeedDefinition(
            feedId = "6802094",
            url = "https://www.cantabria.es/o/BOC/feed/6802094",
            sectionCode = "5", subsectionCode = null, order = 10,
        ),
        BocFeedDefinition(
            feedId = "6802095",
            url = "https://www.cantabria.es/o/BOC/feed/6802095",
            sectionCode = "6", subsectionCode = null, order = 11,
        ),
        BocFeedDefinition(
            feedId = "6802097",
            url = "https://www.cantabria.es/o/BOC/feed/6802097",
            sectionCode = "7", subsectionCode = "7.1", order = 12,
        ),
        BocFeedDefinition(
            feedId = "6802098",
            url = "https://www.cantabria.es/o/BOC/feed/6802098",
            sectionCode = "7", subsectionCode = "7.2", order = 13,
        ),
        BocFeedDefinition(
            feedId = "6802099",
            url = "https://www.cantabria.es/o/BOC/feed/6802099",
            sectionCode = "7", subsectionCode = "7.3", order = 14,
        ),
        BocFeedDefinition(
            feedId = "6802100",
            url = "https://www.cantabria.es/o/BOC/feed/6802100",
            sectionCode = "7", subsectionCode = "7.4", order = 15,
        ),
        BocFeedDefinition(
            feedId = "6802301",
            url = "https://www.cantabria.es/o/BOC/feed/6802301",
            sectionCode = "7", subsectionCode = "7.5", order = 16,
        ),
        BocFeedDefinition(
            feedId = "7479572",
            url = "https://www.cantabria.es/o/BOC/feed/7479572",
            sectionCode = "8", subsectionCode = "8.1", order = 17,
        ),
        BocFeedDefinition(
            feedId = "6802303",
            url = "https://www.cantabria.es/o/BOC/feed/6802303",
            sectionCode = "8", subsectionCode = "8.2", order = 18,
        ),
        BocFeedDefinition(
            feedId = "7293890",
            url = "https://www.cantabria.es/o/BOC/feed/7293890",
            sectionCode = "9", subsectionCode = null, order = 19,
        ),
    )

    val enabled: List<BocFeedDefinition> get() = definitions.filter { it.enabled }

    fun byFeedId(feedId: String): BocFeedDefinition? =
        definitions.firstOrNull { it.feedId == feedId }
}
