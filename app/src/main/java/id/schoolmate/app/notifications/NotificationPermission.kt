package id.schoolmate.app.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class NotificationPermissionStatus {
    /** Notifications can be shown. */
    GRANTED,

    /** Android 13+: the runtime dialog can (still) be shown. */
    NEEDS_REQUEST,

    /** Denied permanently or switched off in system settings – only the settings screen can fix it. */
    BLOCKED,
}

object NotificationPermission {
    /** Pure decision logic, unit-tested for every combination. */
    fun resolve(
        sdkInt: Int,
        runtimeGranted: Boolean,
        appNotificationsEnabled: Boolean,
        requestedBefore: Boolean,
        shouldShowRationale: Boolean,
    ): NotificationPermissionStatus = when {
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !runtimeGranted ->
            if (!requestedBefore || shouldShowRationale) NotificationPermissionStatus.NEEDS_REQUEST
            else NotificationPermissionStatus.BLOCKED
        !appNotificationsEnabled -> NotificationPermissionStatus.BLOCKED
        else -> NotificationPermissionStatus.GRANTED
    }

    fun isRuntimeGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean =
        isRuntimeGranted(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The app's notification page in system settings (available since API 26 = minSdk). */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
