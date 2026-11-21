package me.aroxu.dawnshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

internal data class SettingsSnapshot(
    val enabled: Boolean,
    val allowCeReadableBfu: Boolean,
    val cgroupPolicy: String,
    val pidNamespaceFallback: Boolean,
    val dockerPolicy: String,
    val dockerHostIpc: Boolean,
    val dockerStorage: String,
    val usbMode: String,
    val usbDeviceIds: String,
    val hardwareCodec: Boolean,
) {
    fun usbChanged(previous: SettingsSnapshot) =
        usbMode != previous.usbMode || usbDeviceIds != previous.usbDeviceIds

    fun dockerChanged(previous: SettingsSnapshot) =
        dockerPolicy != previous.dockerPolicy ||
            dockerHostIpc != previous.dockerHostIpc ||
            dockerStorage != previous.dockerStorage

    fun cgroupChanged(previous: SettingsSnapshot) =
        cgroupPolicy != previous.cgroupPolicy || pidNamespaceFallback != previous.pidNamespaceFallback

    fun requiresRuntimeApply(previous: SettingsSnapshot) =
        usbChanged(previous) || dockerChanged(previous) || cgroupChanged(previous)

    companion object {
        fun fromPreferences(context: Context) = SettingsSnapshot(
            BfuPreferences.isEnabled(context),
            BfuPreferences.allowCeReadableBfu(context),
            BfuPreferences.cgroupPolicy(context),
            BfuPreferences.pidNamespaceFallback(context),
            BfuPreferences.dockerNetworkPolicy(context),
            BfuPreferences.dockerHostIpcCompatibility(context),
            BfuPreferences.dockerStorageDriver(context),
            BfuPreferences.usbPassthroughMode(context),
            BfuPreferences.usbExclusiveDeviceIds(context),
            BfuPreferences.hardwareCodecBridge(context),
        )
    }
}

/** Dialogs are queued so that a notice raised while a confirmation is open never replaces it. */
internal sealed interface BootDialog {
    class Notice(val message: String, val title: String? = null) : BootDialog

    class Confirm(
        val title: String,
        val message: String,
        val confirmLabel: String,
        val onConfirm: () -> Unit,
    ) : BootDialog

    class Guide(val title: String, val body: String, val onCopy: () -> Unit) : BootDialog

    class TypedConfirm(
        val title: String,
        val message: String,
        val hint: String,
        val confirmLabel: String,
        val onConfirm: (String) -> Unit,
    ) : BootDialog
}

internal class BootUiState {
    var enableBfu by mutableStateOf(false)
    var allowCeReadableBfu by mutableStateOf(false)
    var pidNamespaceFallback by mutableStateOf(false)
    var cgroupPolicy by mutableStateOf(BfuPreferences.CGROUP_AUTO)
    var dockerPolicy by mutableStateOf(BfuPreferences.DOCKER_HOST_ONLY)
    var dockerStorage by mutableStateOf(BfuPreferences.DOCKER_STORAGE_OVERLAY2)
    var dockerHostIpc by mutableStateOf(false)
    var hardwareCodec by mutableStateOf(false)
    var usbMode by mutableStateOf(BfuPreferences.USB_PASSTHROUGH_OFF)
        private set
    val usbDeviceIds = TextFieldState()
    var usbDeviceIdsError by mutableStateOf<String?>(null)
    var appliedSettings by mutableStateOf<SettingsSnapshot?>(null)

    var rootAuthorizationStatus by mutableStateOf("")
    var installStatus by mutableStateOf("")
    var systemConfigStatus by mutableStateOf("")
    var lifecycleStatus by mutableStateOf("")
    var dockerPolicyStatus by mutableStateOf("")
    var hardwareCodecStatus by mutableStateOf("")
    var hardwareCodecLongRunStatus by mutableStateOf("")
    var probeSummary by mutableStateOf("")
    var generatedPublicKey by mutableStateOf("")

    var rootAuthorizationInProgress by mutableStateOf(false)
    var passwordUpdateInProgress by mutableStateOf(false)
    var codecSelfTestInProgress by mutableStateOf(false)
    var codecControlInProgress by mutableStateOf(false)

    val rootPassword = TextFieldState()
    val rootPasswordConfirm = TextFieldState()
    val debianPassword = TextFieldState()
    val debianPasswordConfirm = TextFieldState()

    val dialogs = mutableStateListOf<BootDialog>()

    fun selectUsbMode(mode: String) {
        usbMode = mode
        if (mode != BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE) usbDeviceIdsError = null
    }

    /** Current form values; an unparsable device list is kept as a marker so that it still counts as a change. */
    fun currentSettings(): SettingsSnapshot {
        val rawIds = usbDeviceIds.text.toString()
        val normalizedIds = try {
            BfuPreferences.normalizeUsbExclusiveDeviceIds(rawIds)
        } catch (e: IllegalArgumentException) {
            "invalid:" + rawIds.trim()
        }
        return SettingsSnapshot(
            enableBfu, allowCeReadableBfu, cgroupPolicy, pidNamespaceFallback, dockerPolicy,
            dockerHostIpc, dockerStorage, usbMode, normalizedIds, hardwareCodec,
        )
    }

    val settingsDirty: Boolean
        get() = appliedSettings?.let { it != currentSettings() } ?: false
}
