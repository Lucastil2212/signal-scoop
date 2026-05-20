package com.signalsoop.app.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Stops optional mesh radio when the app leaves the foreground (defense: no background exposure).
 */
object MeshRadioRegistry {
    @Volatile
    var stopRadio: (() -> Unit)? = null
}

object AppLifecycleGuard : DefaultLifecycleObserver {
    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        MeshRadioRegistry.stopRadio?.invoke()
    }
}
