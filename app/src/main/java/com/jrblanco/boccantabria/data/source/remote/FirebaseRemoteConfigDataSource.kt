package com.jrblanco.boccantabria.data.source.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await

/**
 * The only place in the app allowed to touch [FirebaseRemoteConfig].
 *
 * Packaged defaults are loaded into the client itself, so reading a parameter never comes back
 * empty and neither the repository nor the use case needs a special path for "nothing published
 * yet" (research.md, D-008).
 */
class FirebaseRemoteConfigDataSource(
    private val remoteConfig: FirebaseRemoteConfig,
) : RemoteConfigDataSource {

    override suspend fun fetchValues(): RemoteConfigValues {
        remoteConfig.fetchAndActivate().await()
        return RemoteConfigValues(
            minSupportedVersionCode = remoteConfig.getLong(
                RemoteConfigValues.KEY_MIN_SUPPORTED_VERSION_CODE,
            ),
            maintenanceMessage = remoteConfig.getString(
                RemoteConfigValues.KEY_MAINTENANCE_MESSAGE,
            ),
        )
    }
}

/**
 * Builds the data source with the packaged defaults already loaded.
 *
 * The dependency module lives in `core.di` and must not import the Firebase SDK — that is a
 * layering rule with a test behind it — so the construction belongs here.
 */
fun firebaseRemoteConfigDataSource(): RemoteConfigDataSource {
    val remoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setDefaultsAsync(com.jrblanco.boccantabria.R.xml.remote_config_defaults)
    }
    return FirebaseRemoteConfigDataSource(remoteConfig)
}
