package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.domain.model.AppResult
import com.jrblanco.boccantabria.domain.model.DomainError
import com.jrblanco.boccantabria.domain.model.Publication
import com.jrblanco.boccantabria.domain.repository.SavedPublicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A saved list held in memory, so a use-case or view-model test does not need a database.
 *
 * It keeps the order it is given: the real repository orders in SQL, and a fake that reordered would
 * hide a screen that reorders when it should not.
 */
class FakeSavedPublicationRepository(
    initial: List<Publication> = emptyList(),
) : SavedPublicationRepository {

    private val saved = MutableStateFlow(initial)

    /** Every call received, so a test can assert the delegation instead of guessing at it. */
    val writes = mutableListOf<Pair<String, Boolean>>()

    /** When set, the next write fails. */
    var failWrites: Boolean = false

    override fun observeSaved(): Flow<List<Publication>> = saved

    override fun observeSavedKeys(): Flow<Set<String>> =
        saved.map { publications -> publications.map { it.externalKey }.toSet() }

    override suspend fun setSaved(externalKey: String, saved: Boolean): AppResult<Unit> {
        writes += externalKey to saved
        if (failWrites) return AppResult.Failure(DomainError.Unknown)
        this.saved.value = if (saved) {
            this.saved.value + publication(key = externalKey)
        } else {
            this.saved.value.filterNot { it.externalKey == externalKey }
        }
        return AppResult.Success(Unit)
    }

    fun emit(publications: List<Publication>) {
        saved.value = publications
    }
}
