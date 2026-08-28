package com.jrblanco.boccantabria.data.source.local

interface ConnectivityDataSource {
    fun isOnline(): Boolean
}
