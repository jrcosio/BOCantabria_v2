package com.jrblanco.boccantabria.ui.navigation

import android.content.Intent
import com.jrblanco.boccantabria.core.notification.AlertIntentExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a tapped notification wants the application to land.
 *
 * Held apart from the navigation itself so the cover is never skipped: the activity records the
 * wish, the cover does its checks, and whoever composes the home destination afterwards consumes it
 * (012 research.md D-424). If the cover blocks — minimum version, maintenance — nobody consumes it,
 * which is the right outcome.
 */
sealed interface PendingNavigation {
    data class Publication(val externalKey: String) : PendingNavigation
    data object AlertNews : PendingNavigation
}

/** One pending destination at most, process-wide. A `single` in the graph. */
class PendingNavigationStore {

    private val _pending = MutableStateFlow<PendingNavigation?>(null)
    val pending: StateFlow<PendingNavigation?> = _pending.asStateFlow()

    fun set(navigation: PendingNavigation) {
        _pending.value = navigation
    }

    /** Returns what was pending and clears it, so a recomposition cannot navigate twice. */
    fun consume(): PendingNavigation? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}

/** Reads the extras a notification wrote. Anything else — the launcher itself — is `null`. */
fun Intent.toPendingNavigation(): PendingNavigation? = when (getStringExtra(AlertIntentExtras.EXTRA_TARGET)) {
    AlertIntentExtras.TARGET_PUBLICATION ->
        getStringExtra(AlertIntentExtras.EXTRA_EXTERNAL_KEY)
            ?.takeIf { it.isNotBlank() }
            ?.let { PendingNavigation.Publication(it) }
    AlertIntentExtras.TARGET_NEWS -> PendingNavigation.AlertNews
    else -> null
}
