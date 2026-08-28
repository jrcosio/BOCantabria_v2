package com.jrblanco.boccantabria.domain.model

/**
 * Parameters published by the service that condition startup.
 *
 * The defaults mean "everything allowed": if nothing has been published, the application must not
 * block itself. That is the state of the project today, so it is the path that actually runs.
 */
data class AppConfig(
    val minSupportedVersionCode: Int = 0,
    val maintenanceMessage: String? = null,
)
