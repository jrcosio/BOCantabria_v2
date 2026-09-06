package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.util.AppVisibilityProvider
import com.jrblanco.boccantabria.domain.model.AlertNotification
import com.jrblanco.boccantabria.domain.model.AlertRule
import com.jrblanco.boccantabria.domain.model.InAppAlert
import com.jrblanco.boccantabria.domain.model.KeywordMatchMode
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.repository.AlertNotifier
import com.jrblanco.boccantabria.domain.repository.BackgroundSyncScheduler
import com.jrblanco.boccantabria.domain.repository.InAppAlertStore
import com.jrblanco.boccantabria.domain.repository.NotificationStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared builder so no test has to spell out ten fields to talk about one of them. */
@Suppress("LongParameterList")
fun alertRule(
    id: String = "rule-1",
    name: String = "Ganadería",
    keywords: List<String> = listOf("ganadería"),
    matchMode: KeywordMatchMode = KeywordMatchMode.ANY,
    sectionCodes: Set<String> = emptySet(),
    organizationQuery: String? = null,
    isEnabled: Boolean = true,
    createdAt: Long = 1_000L,
    activeSince: Long = createdAt,
): AlertRule = AlertRule(
    id = id,
    name = name,
    keywords = keywords,
    matchMode = matchMode,
    sectionCodes = sectionCodes,
    organizationQuery = organizationQuery,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = createdAt,
    activeSince = activeSince,
)

/** Records what it was asked to post; nothing reaches Android. */
class RecordingAlertNotifier : AlertNotifier {
    val posted: MutableList<List<AlertNotification>> = mutableListOf()

    override fun post(notifications: List<AlertNotification>) {
        posted += notifications
    }
}

/** The pending in-app message, held in memory exactly like the real store. */
class FakeInAppAlertStore : InAppAlertStore {
    private val pending = MutableStateFlow<InAppAlert?>(null)
    val published: MutableList<InAppAlert> = mutableListOf()
    var consumed: Int = 0
        private set

    override fun observePending(): Flow<InAppAlert?> = pending

    override fun publish(alert: InAppAlert) {
        published += alert
        pending.value = pending.value?.plus(alert) ?: alert
    }

    override fun consume() {
        consumed++
        pending.value = null
    }
}

/** Visibility the test controls. */
class FakeAppVisibilityProvider(var visible: Boolean = false) : AppVisibilityProvider {
    var reads: Int = 0
        private set

    override fun isAppVisible(): Boolean {
        reads++
        return visible
    }
}

/** A scheduler that only remembers what it was told. */
class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
    var scheduled: Int = 0
        private set
    var cancelled: Int = 0
        private set
    val calls: MutableList<String> = mutableListOf()

    override fun ensureScheduled() {
        scheduled++
        calls += "ensureScheduled"
    }

    override fun cancel() {
        cancelled++
        calls += "cancel"
    }
}

class FakeNotificationStatusRepository(
    var status: NotificationStatus = NotificationStatus.GRANTED,
) : NotificationStatusRepository {
    override fun status(): NotificationStatus = status
}
