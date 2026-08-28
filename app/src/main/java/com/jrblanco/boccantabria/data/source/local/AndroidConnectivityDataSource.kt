package com.jrblanco.boccantabria.data.source.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Reports whether there is a network with **validated internet access**.
 *
 * Checking only for an active interface would call a captive hotel Wi-Fi "online", and the startup
 * would then hang waiting for a service that can never answer.
 */
class AndroidConnectivityDataSource(
    private val context: Context,
) : ConnectivityDataSource {

    override fun isOnline(): Boolean {
        val manager = context.getSystemService<ConnectivityManager>() ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
