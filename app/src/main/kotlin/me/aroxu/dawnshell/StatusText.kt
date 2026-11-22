package me.aroxu.dawnshell

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
internal fun StatusLoad.text(
    @StringRes none: Int,
    @StringRes format: Int,
    @StringRes failed: Int,
    maximumLength: Int,
): String = when (this) {
    StatusLoad.Missing -> stringResource(none)
    is StatusLoad.Failed -> stringResource(failed, reason.orEmpty())
    is StatusLoad.Loaded -> stringResource(format, status.display().compactTo(maximumLength))
}

/** Typed launcher events, when the status carries them, replace the raw command line. */
@Composable
private fun OperationStatus.display(): String {
    val events = NativeOutput.fromDetail(detail)
    val running = events.filterIsInstance<LauncherRunning>().lastOrNull()
    val stopped = events.filterIsInstance<LauncherStopped>().lastOrNull()
    val health = events.filterIsInstance<Health>().lastOrNull()
    val launcher = when {
        running != null -> stringResource(
            R.string.bfu_lifecycle_running_detail,
            state.name, running.mode, running.initHostPid, running.cgroupMode, running.hostUsbMode,
        )
        stopped != null -> stringResource(R.string.bfu_lifecycle_stopped_detail, state.name, stopped.lastState)
        else -> return text()
    }
    if (health == null) return launcher
    return launcher + " · " + stringResource(
        if (health.healthy) R.string.bfu_lifecycle_health_ok else R.string.bfu_lifecycle_health_bad,
    )
}

private fun String.compactTo(maximumLength: Int): String {
    val clean = trim()
    return if (clean.length <= maximumLength) clean else clean.substring(0, maxOf(0, maximumLength - 1)) + "…"
}
