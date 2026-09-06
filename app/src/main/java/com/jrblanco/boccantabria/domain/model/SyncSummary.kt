package com.jrblanco.boccantabria.domain.model

/**
 * Outcome of one synchronisation, accumulated across the nineteen sources.
 *
 * It exists so the screen can tell three situations apart that all leave content on display:
 * everything worked, some sources failed, and every source failed but the cache still has
 * content. Only the fourth —every source failed and there is nothing stored— is a failure, and
 * that one never produces a summary.
 *
 * Since feature 012 it also says **which** publications were new, not just how many. [newKeys] used
 * to be what the alerts were evaluated against; since feature 014 the alerts read the store's own
 * pending mark, which the repository sets for exactly these rows as it inserts them, so the work
 * survives a failure or a process death (014 research.md D-607). The keys stay here for the counts,
 * the log and the tests. [isBaseline] marks the first successful synchronisation of an installation,
 * whose thousand-odd "new" rows are history, not news — the repository leaves [newKeys] empty and
 * marks nothing pending in that case, so no consumer can forget to (research.md D-402, D-403).
 */
data class SyncSummary(
    val succeededFeeds: Int = 0,
    val failedFeeds: Int = 0,
    val unchangedFeeds: Int = 0,
    val insertedItems: Int = 0,
    val updatedItems: Int = 0,
    val rejectedItems: Int = 0,
    val newKeys: Set<String> = emptySet(),
    val isBaseline: Boolean = false,
) {
    init {
        require(
            listOf(
                succeededFeeds, failedFeeds, unchangedFeeds,
                insertedItems, updatedItems, rejectedItems,
            ).none { it < 0 },
        ) { "counters must not be negative" }
    }

    /** No source could be reached. Only means "show an error" when there is nothing cached. */
    val allFailed: Boolean get() = succeededFeeds == 0 && failedFeeds > 0

    val isComplete: Boolean get() = failedFeeds == 0

    val attemptedFeeds: Int get() = succeededFeeds + failedFeeds

    operator fun plus(other: SyncSummary): SyncSummary = SyncSummary(
        succeededFeeds = succeededFeeds + other.succeededFeeds,
        failedFeeds = failedFeeds + other.failedFeeds,
        unchangedFeeds = unchangedFeeds + other.unchangedFeeds,
        insertedItems = insertedItems + other.insertedItems,
        updatedItems = updatedItems + other.updatedItems,
        rejectedItems = rejectedItems + other.rejectedItems,
        newKeys = newKeys + other.newKeys,
        isBaseline = isBaseline || other.isBaseline,
    )

    companion object {
        /** What a refresh returns when the cache was still fresh and nothing was attempted. */
        val SKIPPED = SyncSummary()
    }
}
