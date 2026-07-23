package com.kvdm.fuelled.core.connectivity

import kotlinx.coroutines.flow.StateFlow

expect class NetworkMonitor(context: Any?) {
    val isOnline: StateFlow<Boolean>
}
