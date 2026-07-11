package com.kvdm.cmpshowcase.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Desktop dev-client: treat the workstation as always online.
actual class NetworkMonitor actual constructor(context: Any?) {
    actual val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
