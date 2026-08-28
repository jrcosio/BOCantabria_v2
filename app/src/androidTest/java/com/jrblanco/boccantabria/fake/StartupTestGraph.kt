package com.jrblanco.boccantabria.fake

import com.jrblanco.boccantabria.core.util.AppVersionProvider
import com.jrblanco.boccantabria.data.source.local.ConnectivityDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigDataSource
import com.jrblanco.boccantabria.data.source.remote.RemoteConfigValues
import org.koin.core.module.Module
import org.koin.dsl.module

/** Connectivity the test controls, so "offline" needs no airplane mode. */
class FakeConnectivityDataSource(@Volatile var online: Boolean = true) : ConnectivityDataSource {
    override fun isOnline(): Boolean = online
}

/** Remote configuration the test controls, and that counts how many times it was asked. */
class FakeRemoteConfigDataSource(
    @Volatile var values: RemoteConfigValues = DEFAULT_VALUES,
) : RemoteConfigDataSource {

    @Volatile
    var calls: Int = 0
        private set

    override suspend fun fetchValues(): RemoteConfigValues {
        calls++
        return values
    }

    companion object {
        val DEFAULT_VALUES = RemoteConfigValues(minSupportedVersionCode = 0L, maintenanceMessage = "")
    }
}

class FixedAppVersionProvider(override val versionCode: Int = 4) : AppVersionProvider

/**
 * Gives one instrumented test its own startup chain.
 *
 * Instrumented tests share a process and the app graph is made of `single` definitions, so the
 * real Firebase-backed sources would otherwise be reused —and could not even be built without a
 * device. Replacing them per test is what keeps the tests independent of execution order.
 */
fun startupGraphOverrides(
    connectivity: FakeConnectivityDataSource,
    remoteConfig: FakeRemoteConfigDataSource,
    appVersion: AppVersionProvider = FixedAppVersionProvider(),
): List<Module> = listOf(
    module {
        single<ConnectivityDataSource> { connectivity }
        single<RemoteConfigDataSource> { remoteConfig }
        single<AppVersionProvider> { appVersion }
    },
)
