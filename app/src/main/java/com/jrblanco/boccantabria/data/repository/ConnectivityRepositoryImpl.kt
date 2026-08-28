package com.jrblanco.boccantabria.data.repository

import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.domain.repository.ConnectivityRepository

class ConnectivityRepositoryImpl(
    private val connectivityDataSource: ConnectivityDataSource,
) : ConnectivityRepository {

    override fun isOnline(): Boolean = connectivityDataSource.isOnline()
}
