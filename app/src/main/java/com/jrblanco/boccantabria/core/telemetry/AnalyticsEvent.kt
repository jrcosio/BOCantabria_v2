package com.jrblanco.boccantabria.core.telemetry

/**
 * A usage fact worth reporting.
 *
 * The "no personally identifiable information" rule is enforced here rather than documented:
 * [sanitizedParameters] is what implementations must send, and it drops any key that looks
 * like personal data. Keeping the filter in the model means it can be tested without touching
 * any analytics SDK.
 */
data class AnalyticsEvent(
    val name: String,
    val parameters: Map<String, String> = emptyMap(),
) {

    init {
        require(NAME_PATTERN.matches(name)) {
            "Analytics event name must match ${NAME_PATTERN.pattern} but was '$name'"
        }
    }

    /** Parameters with every sensitive key removed. Implementations MUST send only these. */
    fun sanitizedParameters(): Map<String, String> =
        parameters.filterKeys { it.lowercase() !in SENSITIVE_KEYS }

    companion object {
        private val NAME_PATTERN = Regex("^[a-z][a-z0-9_]{0,39}$")

        /** Keys never sent to analytics, whatever the caller passes. */
        val SENSITIVE_KEYS: Set<String> = setOf(
            "address",
            "dni",
            "email",
            "ip",
            "latitude",
            "longitude",
            "name",
            "nie",
            "nif",
            "password",
            "phone",
            "surname",
            "token",
            "user_id",
            "username",
        )

        const val PARAM_SCREEN_NAME: String = "screen_name"
    }
}
