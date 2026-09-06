package com.jrblanco.boccantabria.ui.main

import com.jrblanco.boccantabria.domain.model.InAppAlert

/**
 * What the frame around the four destinations owns: the number on the bell and the message waiting
 * to be shown inside the application.
 *
 * @param pendingAlert consumed by the shell once shown — or once deliberately not shown, when the
 *   person is already looking at the alerts (FR-051).
 */
data class MainShellUiState(
    val unreadAlerts: Int = 0,
    val pendingAlert: InAppAlert? = null,
)
