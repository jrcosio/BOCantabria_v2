package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.util.AppVisibilityProvider
import com.jrblanco.boccantabria.data.source.local.NotificationStatusDataSource
import com.jrblanco.boccantabria.domain.model.AlertNotification
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.repository.AlertNotifier
import com.jrblanco.boccantabria.domain.repository.BackgroundSyncScheduler

/** Mirrors of the unit tests' doubles: instrumented tests cannot see `src/test`. */
class RecordingAlertNotifier : AlertNotifier {
    val posted: MutableList<List<AlertNotification>> = mutableListOf()

    override fun post(notifications: List<AlertNotification>) {
        posted += notifications
    }
}

class FakeAppVisibilityProvider(@Volatile var visible: Boolean = true) : AppVisibilityProvider {
    override fun isAppVisible(): Boolean = visible
}

class FakeBackgroundSyncScheduler : BackgroundSyncScheduler {
    @Volatile var scheduled: Int = 0
    @Volatile var cancelled: Int = 0

    override fun ensureScheduled() {
        scheduled++
    }

    override fun cancel() {
        cancelled++
    }
}

class FakeNotificationStatusDataSource(
    @Volatile var status: NotificationStatus = NotificationStatus.GRANTED,
) : NotificationStatusDataSource {
    override fun status(): NotificationStatus = status
}

/** Mirror of the unit tests' builder. */
@Suppress("LongParameterList")
fun alertRule(
    id: String = "rule-1",
    name: String = "Ganadería",
    keywords: List<String> = listOf("ganadería"),
    matchMode: com.jrblanco.boccantabria.domain.model.KeywordMatchMode = com.jrblanco.boccantabria.domain.model.KeywordMatchMode.ANY,
    sectionCodes: Set<String> = emptySet(),
    organizationQuery: String? = null,
    isEnabled: Boolean = true,
    createdAt: Long = 1_000L,
): com.jrblanco.boccantabria.domain.model.AlertRule = com.jrblanco.boccantabria.domain.model.AlertRule(
    id = id,
    name = name,
    keywords = keywords,
    matchMode = matchMode,
    sectionCodes = sectionCodes,
    organizationQuery = organizationQuery,
    isEnabled = isEnabled,
    createdAt = createdAt,
    updatedAt = createdAt,
    activeSince = createdAt,
)
