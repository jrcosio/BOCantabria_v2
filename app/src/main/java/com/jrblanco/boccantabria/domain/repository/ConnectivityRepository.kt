package com.jrblanco.boccantabria.domain.repository

interface ConnectivityRepository {

    /**
     * Whether the device has a network with **validated internet access**, not merely an active
     * interface: a phone joined to a captive Wi-Fi has no usable connection and must read as
     * offline.
     */
    fun isOnline(): Boolean
}
