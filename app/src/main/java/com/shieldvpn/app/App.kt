package com.shieldvpn.app

import android.app.Application
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel

class App : Application() {
    lateinit var backend: GoBackend
    val tunnel = ShieldTunnel()
    var connectedAt = 0L

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
    }
}

class ShieldTunnel : Tunnel {
    @Volatile
    var listener: ((Tunnel.State) -> Unit)? = null

    override fun getName(): String = "shieldvpn"

    override fun onStateChange(newState: Tunnel.State) {
        listener?.invoke(newState)
    }
}
