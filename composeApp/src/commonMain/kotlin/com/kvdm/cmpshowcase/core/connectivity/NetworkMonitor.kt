package com.kvdm.cmpshowcase.core.connectivity

import kotlinx.coroutines.flow.StateFlow

expect class NetworkMonitor(context: Any?) {
    val isOnline: StateFlow<Boolean>
}
