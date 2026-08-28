package com.jrblanco.boccantabria.data.source.local

interface ContentLocalDataSource {

    suspend fun readContentItems(): List<ContentItemEntity>

    suspend fun writeContentItems(items: List<ContentItemEntity>)
}
