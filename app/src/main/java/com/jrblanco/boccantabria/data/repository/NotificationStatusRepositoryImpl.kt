package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.local.NotificationStatusDataSource
import com.jrblanco.boccantabria.domain.model.NotificationStatus
import com.jrblanco.boccantabria.domain.repository.NotificationStatusRepository

/** Same shape as the connectivity repository: a thin seam over the platform. */
class NotificationStatusRepositoryImpl(
    private val dataSource: NotificationStatusDataSource,
) : NotificationStatusRepository {

    override fun status(): NotificationStatus = dataSource.status()
}
