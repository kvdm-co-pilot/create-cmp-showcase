package com.kvdm.fuelled

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.kvdm.fuelled.notification.NotificationPermissionBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** The in-flight ask, if any — completed by the launcher callback with the user's answer. */
    private var permissionAnswer: CompletableDeferred<Boolean>? = null

    // Registered before STARTED, as the contract requires; launched only through the bridge.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionAnswer?.complete(granted)
            permissionAnswer = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // NOTIF-01: the permission dialog needs an Activity to host it, and this is the only
        // one. The scheduler (application-scoped) reaches it through the bridge; the once-ever
        // policy lives in RequestNotificationPermissionUseCase, not here.
        NotificationPermissionBridge.register {
            withContext(Dispatchers.Main) {
                val answer = CompletableDeferred<Boolean>()
                permissionAnswer = answer
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                answer.await()
            }
        }
        // Edge-to-edge: the status and navigation bars are transparent and content draws
        // beneath them. Screens own their insets in shared code via BaseScreen's Scaffold
        // (statusBarsPadding / navigationBarsPadding), which also maps to iOS safe areas
        // under Compose Multiplatform. Never set the deprecated window.statusBarColor on API 35.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent { App() }
    }

    override fun onDestroy() {
        NotificationPermissionBridge.unregister()
        // A dialog whose Activity died has no answer coming; a null completes as "not shown"
        // upstream because the bridge is empty on the retry, never as a phantom denial.
        permissionAnswer?.cancel()
        permissionAnswer = null
        super.onDestroy()
    }
}
