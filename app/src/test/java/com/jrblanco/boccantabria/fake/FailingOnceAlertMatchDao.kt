package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.data.source.local.AlertMatchDao
import com.jrblanco.boccantabria.data.source.local.AlertMatchEntity

/**
 * The real match store, except that the next [failuresLeft] inserts throw.
 *
 * What the audit found (STAB-003) needs exactly this: a cycle whose recording fails **once**, on a
 * real database, followed by a cycle that finds nothing new at the source. A fake repository cannot
 * show it; a DAO that always fails cannot show the recovery.
 */
class FailingOnceAlertMatchDao(
    private val delegate: AlertMatchDao,
    var failuresLeft: Int = 1,
) : AlertMatchDao by delegate {

    override suspend fun insert(items: List<AlertMatchEntity>): List<Long> {
        if (failuresLeft > 0) {
            failuresLeft--
            throw IllegalStateException("simulated store failure")
        }
        return delegate.insert(items)
    }
}
