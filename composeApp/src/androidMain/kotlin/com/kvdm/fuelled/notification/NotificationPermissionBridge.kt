package com.kvdm.fuelled.notification

/**
 * The seam between the scheduler (application-scoped, no Activity) and the system permission
 * dialog (which only an Activity can launch). [MainActivity] registers its
 * `ActivityResultLauncher` here on create and clears it on destroy;
 * [AndroidReminderScheduler.requestPermission] calls through whatever is currently registered.
 *
 * No Activity registered means no dialog can be shown, and the honest answer is `null` — an
 * ask that never happened is never recorded (NOTIF-01), so the one real ask stays available
 * for a Today open that CAN host it.
 */
object NotificationPermissionBridge {

    private var requester: (suspend () -> Boolean)? = null

    fun register(request: suspend () -> Boolean) {
        requester = request
    }

    fun unregister() {
        requester = null
    }

    suspend fun request(): Boolean? = requester?.invoke()
}
