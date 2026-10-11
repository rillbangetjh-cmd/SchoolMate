package id.schoolmate.app.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.schoolmate.app.R
import id.schoolmate.app.domain.model.NotificationPreferences
import id.schoolmate.app.domain.model.ReminderKind
import id.schoolmate.app.notifications.NotificationPermission
import id.schoolmate.app.notifications.NotificationPermissionStatus
import id.schoolmate.app.ui.AppViewModelProvider
import id.schoolmate.app.util.Formatters

@Composable
fun NotificationSettingsSection(
    viewModel: NotificationSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val p = prefs ?: return
    val context = LocalContext.current
    val activity = LocalActivity.current

    fun readStatus() = NotificationPermission.resolve(
        sdkInt = Build.VERSION.SDK_INT,
        runtimeGranted = NotificationPermission.isRuntimeGranted(context),
        appNotificationsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
        requestedBefore = p.permissionRequested,
        shouldShowRationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS),
    )

    var status by remember { mutableStateOf(readStatus()) }
    // Re-read whenever the user comes back (e.g. from system settings) or the stored flag changes.
    LifecycleResumeEffect(p.permissionRequested) {
        status = readStatus()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { status = readStatus() }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.onPermissionRequested()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openSystemSettings() {
        try { context.startActivity(NotificationPermission.settingsIntent(context)) } catch (_: ActivityNotFoundException) { }
    }

    NotificationSettingsContent(
        prefs = p,
        status = status,
        onEnabledChange = { enabled ->
            viewModel.setEnabled(enabled)
            // Ask at the moment the user opts in – never on app start.
            if (enabled && status == NotificationPermissionStatus.NEEDS_REQUEST) requestPermission()
        },
        onKindChange = viewModel::setKindEnabled,
        onLeadChange = viewModel::setLeadMinutes,
        onRequestPermission = ::requestPermission,
        onOpenSettings = ::openSystemSettings,
    )
}

@Composable
internal fun NotificationSettingsContent(
    prefs: NotificationPreferences,
    status: NotificationPermissionStatus,
    onEnabledChange: (Boolean) -> Unit,
    onKindChange: (ReminderKind, Boolean) -> Unit,
    onLeadChange: (ReminderKind, Int) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        ListItem(
            leadingContent = {
                Icon(if (prefs.enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff, contentDescription = null)
            },
            headlineContent = { Text(stringResource(R.string.notif_master)) },
            supportingContent = { Text(stringResource(R.string.notif_master_desc)) },
            trailingContent = {
                Switch(checked = prefs.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.testTag("notif_master"))
            },
        )

        PermissionRow(status, prefs.enabled, onRequestPermission, onOpenSettings)

        AnimatedVisibility(visible = prefs.enabled) {
            Column {
                KindRow(
                    kind = ReminderKind.TASK, title = R.string.notif_tasks, leadLabel = R.string.notif_tasks_lead,
                    on = prefs.taskReminders, lead = prefs.taskLeadMinutes, options = NotificationPreferences.TASK_LEAD_OPTIONS,
                    onKindChange = onKindChange, onLeadChange = onLeadChange,
                )
                KindRow(
                    kind = ReminderKind.EXAM, title = R.string.notif_exams, leadLabel = R.string.notif_exams_lead,
                    on = prefs.examReminders, lead = prefs.examLeadMinutes, options = NotificationPreferences.EXAM_LEAD_OPTIONS,
                    onKindChange = onKindChange, onLeadChange = onLeadChange,
                )
                KindRow(
                    kind = ReminderKind.LESSON, title = R.string.notif_lessons, leadLabel = R.string.notif_lessons_lead,
                    on = prefs.lessonReminders, lead = prefs.lessonLeadMinutes, options = NotificationPreferences.LESSON_LEAD_OPTIONS,
                    onKindChange = onKindChange, onLeadChange = onLeadChange,
                )
                Text(
                    stringResource(R.string.notif_timing_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    status: NotificationPermissionStatus,
    enabled: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (label, warn) = when (status) {
        NotificationPermissionStatus.GRANTED -> R.string.notif_permission_granted to false
        NotificationPermissionStatus.NEEDS_REQUEST -> R.string.notif_permission_needed to enabled
        NotificationPermissionStatus.BLOCKED -> R.string.notif_permission_blocked to enabled
    }
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notif_permission_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("notif_permission_status"))
            }
            if (status == NotificationPermissionStatus.NEEDS_REQUEST) {
                TextButton(onClick = onRequestPermission) { Text(stringResource(R.string.notif_permission_allow)) }
            } else {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.notif_open_settings)) }
            }
        }
    }
    if (warn) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) { content() }
    } else {
        Column(Modifier.padding(horizontal = 4.dp)) { content() }
    }
}

@Composable
private fun KindRow(
    kind: ReminderKind,
    title: Int,
    leadLabel: Int,
    on: Boolean,
    lead: Int,
    options: List<Int>,
    onKindChange: (ReminderKind, Boolean) -> Unit,
    onLeadChange: (ReminderKind, Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(leadLabel)) },
        trailingContent = {
            Switch(checked = on, onCheckedChange = { onKindChange(kind, it) }, modifier = Modifier.testTag("notif_${kind.slug}"))
        },
    )
    AnimatedVisibility(visible = on) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { minutes ->
                FilterChip(
                    selected = minutes == lead,
                    onClick = { onLeadChange(kind, minutes) },
                    label = { Text(Formatters.leadTime(minutes)) },
                    modifier = Modifier.testTag("lead_${kind.slug}_$minutes"),
                )
            }
        }
    }
}
