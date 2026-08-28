package com.jrblanco.boccantabria.data.source.remote

/**
 * Configuration as the remote service delivers it, with its types and its conventions.
 *
 * The service returns `Long` for numbers and an empty string for unset text; translating to the
 * domain adjusts both. Keeping the shapes apart makes the boundary visible instead of pretending
 * the two layers speak the same language.
 */
data class RemoteConfigValues(
    val minSupportedVersionCode: Long,
    val maintenanceMessage: String,
) {
    companion object {
        const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
        const val KEY_MAINTENANCE_MESSAGE = "maintenance_message"
    }
}
