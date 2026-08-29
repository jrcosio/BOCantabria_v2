package com.jrblanco.boccantabria.domain.model

/**
 * Outcome of one synchronisation, accumulated across the nineteen sources.
 *
 * It exists so the screen can tell three situations apart that all leave content on display:
 * everything worked, some sources failed, and every source failed but the cache still has
 * content. Only the fourth —every source failed and there is nothing stored— is a failure, and
 * that one never produces a summary.
 */
data class SyncSummary(
    val succeededFeeds: Int = 0,
    val failedFeeds: Int = 0,
    val unchangedFeeds: Int = 0,
    val insertedItems: Int = 0,
    val updatedItems: Int = 0,
    val rejectedItems: Int = 0,
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
    )

    companion object {
        /** What a refresh returns when the cache was still fresh and nothing was attempted. */
        val SKIPPED = SyncSummary()
    }
}
