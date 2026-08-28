package com.jrblanco.boccantabria.domain.repository

import com.jrblanco.boccantabria.domain.model.AppConfig
import com.jrblanco.boccantabria.domain.model.AppResult

interface AppConfigRepository {

    /** Never throws: failures come back as [AppResult.Failure]. */
    suspend fun loadConfig(): AppResult<AppConfig>
}
