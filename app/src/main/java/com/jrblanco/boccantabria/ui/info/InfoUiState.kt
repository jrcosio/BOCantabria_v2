package com.jrblanco.boccantabria.ui.info

import androidx.compose.runtime.Immutable

@Immutable
data class InfoUiState(
    val versionName: String = "",
    val linkOpenFailed: Boolean = false,
)

enum class InfoLink(
    val analyticsValue: String,
    val url: String,
) {
    LINKEDIN(
        analyticsValue = "linkedin",
        url = "https://www.linkedin.com/in/jr-blanco/",
    ),
    GITHUB(
        analyticsValue = "github",
        url = "https://github.com/jrcosio/BOCantabria_v2.git",
    ),
}
