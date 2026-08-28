package com.jrblanco.boccantabria.data.source.remote

interface RemoteConfigDataSource {

    /** May throw. The repository is the one that catches and translates. */
    suspend fun fetchValues(): RemoteConfigValues
}
