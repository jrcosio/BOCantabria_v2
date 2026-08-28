package com.jrblanco.boccantabria.ui.splash

import com.jrblanco.boccantabria.domain.model.DomainError

/**
 * What the splash screen shows at a given moment.
 *
 * [Blocked] is a state of its own rather than an [Error] carrying a flag. A recoverable error
 * offers "continue offline"; a blocked access must not, because letting the user through defeats
 * the point of blocking them. As separate types the incoherent combination cannot even be written
 * (research.md, D-007).
 */
sealed interface SplashUiState {

    data object Loading : SplashUiState

    data object Ready : SplashUiState

    data class Error(val error: DomainError) : SplashUiState

    data class Blocked(val reason: BlockReason) : SplashUiState
}

sealed interface BlockReason {

    data object UpdateRequired : BlockReason

    data class Maintenance(val message: String) : BlockReason
}
