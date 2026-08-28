package com.jrblanco.boccantabria.domain.model

/**
 * Result of any domain operation.
 *
 * Deliberately not [kotlin.Result]: wrapping a [Throwable] would drag exceptions into the
 * domain and make failures opaque to the presentation layer. With a sealed [DomainError] the
 * screen decides what to show through an exhaustive `when` the compiler checks.
 *
 * Absence of content is not a failure: an empty list is `Success(emptyList())`.
 */
sealed interface AppResult<out T> {

    data class Success<out T>(val data: T) : AppResult<T>

    data class Failure(val error: DomainError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
