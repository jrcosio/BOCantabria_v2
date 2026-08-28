package com.jrblanco.boccantabria.ui.home

import com.jrblanco.boccantabria.domain.model.ContentItem
import com.jrblanco.boccantabria.domain.model.DomainError

/**
 * What the home screen must show at a given moment.
 *
 * Sealed rather than a data class with flags: the specification requires the four states to be
 * mutually exclusive, and a class with `isLoading` plus `errorMessage` allows impossible
 * combinations that then have to be prevented by convention. Here they are impossible by
 * construction, and tests assert on the type directly.
 */
sealed interface HomeUiState {

    data object Loading : HomeUiState

    data class Content(val items: List<ContentItem>) : HomeUiState

    data object Empty : HomeUiState

    data class Error(val error: DomainError) : HomeUiState
}
