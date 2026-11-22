package me.aroxu.dawnshell

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.util.Base64
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.aroxu.dawnshell.ui.DawnShellTheme

internal class BootActivity : AppCompatActivity(), BootActions {

    private val state = BootUiState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val rootAuthorizationExecutor = Executors.newSingleThreadExecutor()
    private val passwordExecutor = Executors.newSingleThreadExecutor()
    private val codecSelfTestExecutor = Executors.newSingleThreadExecutor()
    private val codecControlExecutor = Executors.newSingleThreadExecutor()
    private var activityResumed = false
    private var pendingRootAuthorizationResult: BfuRootAuthorization.Result? = null
    private var pendingRootAuthorizationFailure: String? = null

    private val exportPrivateKey =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destination ->
            if (destination != null) writePrivateKey(destination)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
        setContent {
            // HACK: AppCompatActivity does not publish its NavigationEventDispatcherOwner to the view tree.
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
                DawnShellTheme {
                    BootScreen(state, this, onDiscard = ::finish)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshLiveStatus()
                    delay(1_000L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        showPendingRootAuthorizationResult()
    }

    override fun onPause() {
        activityResumed = false
        super.onPause()
    }

    override fun onDestroy() {
        rootAuthorizationExecutor.shutdownNow()
        passwordExecutor.shutdownNow()
        codecSelfTestExecutor.shutdownNow()
        codecControlExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun writePrivateKey(destination: Uri) {
        try {
            val identity = BfuSshClientKeyStore.ensure(this)
            contentResolver.openOutputStream(destination, "wt").use { output ->
                if (output == null) throw IOException("document provider returned no stream")
                output.write(identity.privateKey.toByteArray(Charsets.US_ASCII))
                output.flush()
            }
            recordOperation("SSH_CLIENT_PRIVATE_KEY_EXPORTED destination=document_provider")
            notice(R.string.bfu_private_key_exported)
        } catch (e: IOException) {
            recordOperation("SSH_CLIENT_PRIVATE_KEY_EXPORT_FAILED " + BfuSu.sanitize(e.message))
            notice(getString(R.string.bfu_private_key_export_failed_detail, e.message))
        }
    }

    private fun refreshLiveStatus() {
        refreshRootAuthorizationStatus()
        refreshInstallerStatus()
        refreshSystemConfigurationStatus()
        refreshLifecycleStatus()
        refreshDockerPolicyStatus()
        refreshHardwareCodecStatus()
        refreshHardwareCodecLongRunStatus()
    }

    // Dialogs

    private fun notice(@StringRes message: Int) = notice(getString(message))

    private fun notice(message: String?) {
        if (isFinishing || message.isNullOrBlank()) return
        state.dialogs += BootDialog.Notice(message)
    }

    private fun confirm(
        @StringRes title: Int,
        @StringRes message: Int,
        @StringRes confirmLabel: Int,
        onConfirm: () -> Unit,
    ) {
        state.dialogs += BootDialog.Confirm(getString(title), getString(message), getString(confirmLabel), onConfirm)
    }

    private inline fun tryProvision(block: () -> Unit, onFailure: (Exception) -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            onFailure(e)
        } catch (e: IllegalStateException) {
            onFailure(e)
        }
    }

    private inline fun provisionOrNull(onFailure: (Exception) -> Unit): BfuRuntime.Layout? = try {
        BfuRuntime.provision(this)
    } catch (e: IOException) {
        onFailure(e)
        null
    } catch (e: IllegalStateException) {
        onFailure(e)
        null
    }

    // Navigation

    override fun openLogs() = startActivity(Intent(this, LogsActivity::class.java))

    override fun openLog(type: String) = startActivity(LogDetailActivity.createIntent(this, type))

    override fun openLicenses() = startActivity(Intent(this, OpenSourceLicensesActivity::class.java))

    // SSH access

    /*
    private fun refreshGeneratedSshIdentity(provisionPublicKey: Boolean) {
        state.generatedPublicKey = try {
            val identity = BfuSshClientKeyStore.ensure(this)
            if (provisionPublicKey) {
                val layout = BfuRuntime.provision(this)
                BfuAuthorizedKeys.validateAndSave(layout, identity.publicKey)
            }
            identity.publicKey
        } catch (e: IOException) {
            getString(R.string.bfu_generated_key_failed, e.message)
        }
    }
     */

    override fun confirmRotateSshKey() {
        if (!isUserUnlocked()) {
            notice(R.string.bfu_ssh_key_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_rotate_ssh_key_confirm_title,
            R.string.bfu_rotate_ssh_key_confirm_message,
            R.string.bfu_rotate_ssh_key_confirm_button,
            ::rotateSshClientKey,
        )
    }

    private fun rotateSshClientKey() {
        try {
            val identity = BfuSshClientKeyStore.generateAndReplace(this)
            val layout = BfuRuntime.provision(this)
            BfuAuthorizedKeys.validateAndSave(layout, identity.publicKey)
            //state.generatedPublicKey = identity.publicKey
            recordOperation("SSH_CLIENT_KEY_ROTATED algorithm=ed25519 de_public_key_updated=true")
            state.dialogs += BootDialog.Notice(
                getString(R.string.bfu_rotate_ssh_key_done_message),
                getString(R.string.bfu_rotate_ssh_key_done_title),
            )
        } catch (e: IOException) {
            recordOperation("SSH_CLIENT_KEY_ROTATE_FAILED " + BfuSu.sanitize(e.message))
            notice(getString(R.string.bfu_generated_key_failed, e.message))
        }
    }

    override fun confirmExportPrivateKey() {
        if (!isUserUnlocked()) {
            notice(R.string.bfu_ssh_key_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_export_private_key_confirm_title,
            R.string.bfu_export_private_key_confirm_message,
            R.string.bfu_export_private_key_confirm_button,
        ) { exportPrivateKey.launch("dawnshell-ed25519") }
    }

    override fun confirmCopyKeyImportCommand() {
        if (!isUserUnlocked()) {
            notice(R.string.bfu_ssh_key_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_copy_key_import_confirm_title,
            R.string.bfu_copy_key_import_confirm_message,
            R.string.bfu_copy_key_import_confirm_button,
            ::copyKeyImportCommand,
        )
    }

    private fun copyKeyImportCommand() {
        try {
            val command = buildKeyImportCommand(BfuSshClientKeyStore.ensure(this))
            copySshClientCommand("key_import", command, R.string.bfu_key_import_command_copied)
            mainHandler.postDelayed({ clearSensitiveClipboard(command) }, 120_000L)
        } catch (e: IOException) {
            recordOperation("SSH_CLIENT_KEY_IMPORT_COPY_FAILED " + BfuSu.sanitize(e.message))
            notice(getString(R.string.bfu_generated_key_failed, e.message))
        }
    }

    private fun clearSensitiveClipboard(expected: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        val clip = clipboard?.primaryClip
        if (clipboard == null || !clipboard.hasPrimaryClip() || clip == null || clip.itemCount == 0) return
        if (expected != clip.getItemAt(0).coerceToText(this).toString()) return
        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        recordOperation("SSH_CLIENT_PRIVATE_KEY_CLIPBOARD_CLEARED timeout_seconds=120")
    }

    private fun buildKeyImportCommand(identity: BfuSshClientKeyStore.Identity): String {
        val encoded = Base64.encodeToString(identity.privateKey.toByteArray(Charsets.US_ASCII), Base64.NO_WRAP)
        return "set -eu; umask 077; KEY=\"\$HOME/.ssh/dawnshell-ed25519\"; " +
            "mkdir -p \"\$HOME/.ssh\"; chmod 0700 \"\$HOME/.ssh\"; " +
            "printf '%s' '" + encoded + "' | base64 -d > \"\$KEY\"; " +
            "chmod 0600 \"\$KEY\"; " +
            "printf 'Imported DawnShell client key: %s\\n' \"\$KEY\""
    }

    override fun buildSshConnectCommand() =
        "ssh -i \"\$HOME/.ssh/dawnshell-ed25519\" -p 22 " +
            "-o IdentitiesOnly=yes -o PasswordAuthentication=no " +
            "-o StrictHostKeyChecking=accept-new " +
            "-o UserKnownHostsFile=\"\$HOME/.ssh/dawnshell-known_hosts\" " +
            "-o ConnectTimeout=10 debian@127.0.0.1"

    override fun copySshConnectCommand() =
        copySshClientCommand("ssh_connect", buildSshConnectCommand(), R.string.bfu_ssh_connect_command_copied)

    private fun copySshClientCommand(operation: String, command: String, @StringRes noticeMessage: Int) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard == null) {
            notice(R.string.bfu_clipboard_unavailable)
            recordOperation("SSH_CLIENT_COMMAND_COPY_FAILED type=$operation clipboard_unavailable=true")
            return
        }
        val clip = ClipData.newPlainText("DawnShell command", command)
        if (Build.VERSION.SDK_INT >= 33) {
            val extras = PersistableBundle()
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            clip.description.extras = extras
        }
        clipboard.setPrimaryClip(clip)
        recordOperation("SSH_CLIENT_COMMAND_COPIED type=$operation")
        notice(noticeMessage)
    }

    // Settings

    private fun loadSettings() {
        state.enableBfu = BfuPreferences.isEnabled(this)
        state.allowCeReadableBfu = BfuPreferences.allowCeReadableBfu(this)
        state.pidNamespaceFallback = BfuPreferences.pidNamespaceFallback(this)
        state.cgroupPolicy = BfuPreferences.cgroupPolicy(this)
        state.dockerPolicy = BfuPreferences.dockerNetworkPolicy(this)
        state.dockerStorage = BfuPreferences.dockerStorageDriver(this)
        state.dockerHostIpc = BfuPreferences.dockerHostIpcCompatibility(this)
        state.hardwareCodec = BfuPreferences.hardwareCodecBridge(this)
        state.selectUsbMode(BfuPreferences.usbPassthroughMode(this))
        state.usbDeviceIds.setTextAndPlaceCursorAtEnd(BfuPreferences.usbExclusiveDeviceIds(this))
        //refreshGeneratedSshIdentity(true)
        refreshRootAuthorizationStatus()
        refreshProbeStatus(false)
        refreshInstallerStatus()
        refreshSystemConfigurationStatus()
        refreshLifecycleStatus()
        refreshDockerPolicyStatus()
        refreshHardwareCodecStatus()
        state.appliedSettings = SettingsSnapshot.fromPreferences(this)
    }

    private inline fun readProbe(@StringRes none: Int, @StringRes failed: Int, read: () -> String): String = try {
        read().ifEmpty { getString(none) }
    } catch (e: IOException) {
        getString(failed, e.message)
    }

    override fun refreshProbes() = refreshProbeStatus(true)

    private fun refreshProbeStatus(recordOperation: Boolean) {
        val rootResult = readProbe(R.string.bfu_root_probe_none, R.string.bfu_root_probe_read_failed) {
            BfuRootProbe.readLastPersistentResult(this)
        }
        val ceIsolationResult = readProbe(
            R.string.bfu_ce_isolation_probe_none, R.string.bfu_ce_isolation_probe_read_failed,
        ) { BfuCeIsolationProbe.readLastPersistentResult(this) }
        val rootfsResult = readProbe(R.string.bfu_rootfs_probe_none, R.string.bfu_rootfs_probe_read_failed) {
            BfuRootfsProbe.readLastPersistentResult(this)
        }
        val runtimeResult = readProbe(
            R.string.bfu_debian_runtime_probe_none, R.string.bfu_debian_runtime_probe_read_failed,
        ) { BfuDebianRuntimeProbe.readLastPersistentResult(this) }

        val compactProbe = if (runtimeResult.contains(getString(R.string.bfu_debian_runtime_probe_none))) {
            rootfsResult
        } else {
            runtimeResult
        }
        state.probeSummary = getString(R.string.dawnshell_probe_summary, compact(oneLine(compactProbe), 240))

        if (recordOperation) {
            recordOperation(
                "PROBE_RESULTS_REFRESHED root={" + oneLine(rootResult) +
                    "} ce_isolation={" + oneLine(ceIsolationResult) +
                    "} rootfs={" + oneLine(rootfsResult) +
                    "} runtime={" + oneLine(runtimeResult) + "}",
            )
        }
    }

    private fun requestedSettings(): SettingsSnapshot {
        val rawIds = state.usbDeviceIds.text.toString()
        val normalizedIds = try {
            BfuPreferences.normalizeUsbExclusiveDeviceIds(rawIds)
        } catch (e: IllegalArgumentException) {
            rejectUsbDeviceIds(R.string.dawnshell_usb_exclusive_ids_invalid)
        }
        if (state.usbMode == BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE && normalizedIds.isEmpty()) {
            rejectUsbDeviceIds(R.string.dawnshell_usb_exclusive_ids_required)
        }
        return state.currentSettings()
    }

    private fun rejectUsbDeviceIds(@StringRes message: Int): Nothing {
        val text = getString(message)
        state.usbDeviceIdsError = text
        throw IllegalStateException(text)
    }

    override fun confirmApplySettings() {
        val requested = try {
            requestedSettings()
        } catch (e: IllegalStateException) {
            notice(e.message)
            return
        }
        val previous = SettingsSnapshot.fromPreferences(this)
        if (previous == requested) {
            state.appliedSettings = previous
            notice(R.string.dawnshell_apply_changes_none)
            return
        }
        if (requested.requiresRuntimeApply(previous) && !isUserUnlocked()) {
            notice(R.string.dawnshell_apply_changes_requires_unlock)
            return
        }
        if (requested.requiresRuntimeApply(previous) || (!previous.allowCeReadableBfu && requested.allowCeReadableBfu)) {
            confirm(
                R.string.dawnshell_apply_changes_confirm_title,
                R.string.dawnshell_apply_changes_confirm_message,
                R.string.dawnshell_apply_changes,
            ) { applySettings(previous, requested) }
            return
        }
        applySettings(previous, requested)
    }

    private fun applySettings(previous: SettingsSnapshot, requested: SettingsSnapshot) {
        recordOperation(
            "SETTINGS_APPLY_STARTED enable_bfu=" + requested.enabled +
                " allow_ce_readable_bfu=" + requested.allowCeReadableBfu +
                " pid_namespace_fallback=" + requested.pidNamespaceFallback +
                " usb_passthrough_mode=" + requested.usbMode +
                " cgroup_policy=" + requested.cgroupPolicy +
                " docker_network_policy=" + requested.dockerPolicy +
                " docker_host_ipc_compatibility=" + requested.dockerHostIpc +
                " hardware_codec_bridge=" + requested.hardwareCodec,
        )
        tryProvision(
            block = {
                savePreferences(requested)
                BfuCeIsolationProbe.provisionSentinel(this)
                val layout = BfuRuntime.provision(this)
                val identity = BfuSshClientKeyStore.ensure(this)
                val keyCount = BfuAuthorizedKeys.validateAndSave(layout, identity.publicKey)
                recordOperation("SETTINGS_APPLY_SAVED runtime=" + layout.root + " authorized_key_count=" + keyCount)

                if (requested.hardwareCodec) {
                    BfuBootService.requestHardwareCodecProbe(this)
                } else {
                    HardwareCodecService.stop(this)
                }

                val runtimeApply = requested.requiresRuntimeApply(previous)
                val activateNow = runtimeApply && isUserUnlocked()
                if (activateNow) {
                    BfuBootService.requestRuntimeSettingsApply(
                        this,
                        requested.usbChanged(previous),
                        requested.dockerChanged(previous),
                        requested.cgroupChanged(previous),
                    )
                }
                state.appliedSettings = requested
                notice(
                    if (activateNow) {
                        R.string.dawnshell_apply_changes_applied
                    } else if (runtimeApply) {
                        R.string.dawnshell_apply_changes_saved_for_next_start
                    } else {
                        R.string.dawnshell_apply_changes_applied
                    },
                )
            },
            onFailure = { e ->
                recordOperation("SETTINGS_APPLY_FAILED " + BfuSu.sanitize(e.message))
                notice(getString(R.string.bfu_provision_failed, e.message))
            },
        )
    }

    private fun savePreferences(settings: SettingsSnapshot) {
        BfuPreferences.save(
            this, settings.enabled, settings.allowCeReadableBfu, settings.cgroupPolicy,
            settings.pidNamespaceFallback, settings.dockerPolicy, settings.dockerHostIpc,
            settings.dockerStorage, settings.usbMode, settings.usbDeviceIds, settings.hardwareCodec,
        )
        state.usbDeviceIds.setTextAndPlaceCursorAtEnd(settings.usbDeviceIds)
        state.usbDeviceIdsError = null
    }

    // Root authorization

    override fun confirmRootAuthorization() {
        if (!isUserUnlocked()) {
            recordOperation("ROOT_AUTHORIZATION_REJECTED user_locked=true")
            notice(R.string.bfu_root_authorization_requires_unlock)
            return
        }
        val uid = Process.myUid()
        state.dialogs += BootDialog.Confirm(
            getString(R.string.bfu_root_authorization_confirm_title),
            getString(R.string.bfu_root_authorization_confirm_message, uid.toString(), packagesForUid(uid)),
            getString(R.string.bfu_root_authorization_confirm_button),
            ::requestRootAuthorization,
        )
    }

    private fun requestRootAuthorization() {
        if (state.rootAuthorizationInProgress) return
        state.rootAuthorizationInProgress = true
        state.rootAuthorizationStatus = getString(R.string.bfu_root_authorization_waiting)
        recordOperation("ROOT_AUTHORIZATION_STARTED shared_uid=" + Process.myUid())
        val applicationContext = applicationContext

        rootAuthorizationExecutor.execute {
            var result: BfuRootAuthorization.Result? = null
            var failure: String? = null
            try {
                result = BfuRootAuthorization.request(applicationContext)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                failure = applicationContext.getString(R.string.bfu_root_authorization_interrupted)
            } catch (e: IOException) {
                failure = BfuSu.sanitize(e.message)
            } catch (e: IllegalStateException) {
                failure = BfuSu.sanitize(e.message)
            }
            val completedResult = result
            val completedFailure = failure
            mainHandler.post { finishRootAuthorization(completedResult, completedFailure) }
        }
    }

    private fun finishRootAuthorization(result: BfuRootAuthorization.Result?, failure: String?) {
        if (isFinishing || isDestroyed) return
        state.rootAuthorizationInProgress = false
        refreshRootAuthorizationStatus()

        pendingRootAuthorizationResult = result
        pendingRootAuthorizationFailure = failure
        if (result != null) {
            recordOperation(
                (if (result.authorizedWhileUnlocked()) "ROOT_AUTHORIZATION_SUCCEEDED " else "ROOT_AUTHORIZATION_FAILED ") +
                    result.summary(),
            )
        } else {
            recordOperation("ROOT_AUTHORIZATION_FAILED " + (failure ?: "unknown failure"))
        }
        if (activityResumed) showPendingRootAuthorizationResult()
    }

    private fun showPendingRootAuthorizationResult() {
        val result = pendingRootAuthorizationResult
        val failure = pendingRootAuthorizationFailure
        if (result == null && failure == null) return
        pendingRootAuthorizationResult = null
        pendingRootAuthorizationFailure = null

        if (result != null && result.authorizedWhileUnlocked()) {
            state.dialogs += BootDialog.Notice(
                getString(R.string.bfu_root_authorization_verified_message, result.appUid.toString()),
                getString(R.string.bfu_root_authorization_verified_title),
            )
            return
        }
        val reason = failure ?: result?.summary() ?: getString(R.string.bfu_root_authorization_unknown_failure)
        state.dialogs += BootDialog.Notice(
            getString(R.string.bfu_root_authorization_failed_message, reason),
            getString(R.string.bfu_root_authorization_failed_title),
        )
    }

    private fun refreshRootAuthorizationStatus() {
        if (state.rootAuthorizationInProgress) return
        state.rootAuthorizationStatus = try {
            val result = BfuRootAuthorization.readLastPersistentResult(this)
                .ifEmpty { getString(R.string.bfu_root_authorization_none) }
            getString(R.string.bfu_root_authorization_status, result)
        } catch (e: IOException) {
            getString(R.string.bfu_root_authorization_read_failed, e.message)
        }
    }

    private fun packagesForUid(uid: Int): String {
        val packages = packageManager.getPackagesForUid(uid)
        if (packages.isNullOrEmpty()) return packageName
        return packages.sorted().joinToString("\n") { "• $it" }
    }

    // Debian installation and configuration

    override fun confirmDebianInstall() {
        if (!BfuPreferences.isEnabled(this)) {
            recordOperation("DEBIAN_INSTALL_REJECTED bfu_disabled=true")
            notice(R.string.bfu_install_requires_enabled)
            return
        }
        if (!isUserUnlocked()) {
            recordOperation("DEBIAN_INSTALL_REJECTED user_locked=true")
            notice(R.string.bfu_install_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_install_confirm_title, R.string.bfu_install_confirm_message,
            R.string.bfu_install_confirm_button, ::startDebianInstall,
        )
    }

    override fun confirmRootfsRemoval() {
        if (!isUserUnlocked()) {
            recordOperation("DEBIAN_ROOTFS_REMOVE_REJECTED user_locked=true")
            notice(R.string.bfu_remove_rootfs_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_remove_rootfs_confirm_title, R.string.bfu_remove_rootfs_confirm_message,
            R.string.bfu_remove_rootfs_continue, ::confirmTypedDebianRootfsRemoval,
        )
    }

    private fun confirmTypedDebianRootfsRemoval() {
        state.dialogs += BootDialog.TypedConfirm(
            getString(R.string.bfu_remove_rootfs_final_title),
            getString(R.string.bfu_remove_rootfs_final_message),
            getString(R.string.bfu_remove_rootfs_type_hint),
            getString(R.string.bfu_remove_rootfs_button),
        ) { value ->
            if (value != "DELETE") {
                notice(R.string.bfu_remove_rootfs_confirmation_mismatch)
            } else {
                BfuBootService.requestDebianRootfsRemoval(this)
                recordOperation("DEBIAN_ROOTFS_REMOVE_REQUESTED root=/data/local/debian")
                notice(R.string.bfu_remove_rootfs_requested)
            }
        }
    }

    private fun startDebianInstall() = tryProvision(
        block = {
            BfuCeIsolationProbe.provisionSentinel(this)
            val layout = BfuRuntime.provision(this)
            BfuBootService.requestDebianRootfsInstall(this)
            recordOperation(
                "DEBIAN_INSTALL_REQUESTED suite=trixie architecture=" + layout.architecture.debianArchitecture +
                    " android_abi=" + layout.architecture.androidAbi,
            )
            notice(R.string.bfu_install_requested)
            refreshInstallerStatus()
        },
        onFailure = { e ->
            recordOperation("DEBIAN_INSTALL_REQUEST_FAILED " + BfuSu.sanitize(e.message))
            notice(getString(R.string.bfu_provision_failed, e.message))
        },
    )

    override fun confirmSystemConfiguration() {
        if (!BfuPreferences.isEnabled(this)) {
            recordOperation("DEBIAN_CONFIG_REJECTED bfu_disabled=true")
            notice(R.string.bfu_install_requires_enabled)
            return
        }
        if (!isUserUnlocked()) {
            recordOperation("DEBIAN_CONFIG_REJECTED user_locked=true")
            notice(R.string.bfu_system_config_requires_unlock)
            return
        }
        confirm(
            R.string.bfu_system_config_confirm_title, R.string.bfu_system_config_confirm_message,
            R.string.bfu_system_config_confirm_button, ::startSystemConfiguration,
        )
    }

    private fun startSystemConfiguration() = tryProvision(
        block = {
            BfuCeIsolationProbe.provisionSentinel(this)
            val layout = BfuRuntime.provision(this)
            val identity = BfuSshClientKeyStore.ensure(this)
            val keyCount = BfuAuthorizedKeys.validateAndSave(layout, identity.publicKey)
            BfuBootService.requestDebianSystemConfiguration(this)
            recordOperation(
                "DEBIAN_CONFIG_REQUESTED suite=trixie ssh_user=debian ssh_port=22 authorized_key_count=" + keyCount,
            )
            notice(R.string.bfu_system_config_requested)
            refreshSystemConfigurationStatus()
        },
        onFailure = { e ->
            recordOperation("DEBIAN_CONFIG_REQUEST_FAILED " + BfuSu.sanitize(e.message))
            notice(getString(R.string.bfu_system_config_request_failed, e.message))
        },
    )

    // Hardware codec

    override fun probeHardwareCodecs() {
        if (!BfuPreferences.hardwareCodecBridge(this)) {
            recordOperation("HARDWARE_CODEC_PROBE_REJECTED enabled=false")
            notice(R.string.dawnshell_codec_apply_first)
            return
        }
        BfuBootService.requestHardwareCodecProbe(this)
        recordOperation("HARDWARE_CODEC_PROBE_REQUESTED user_unlocked=" + isUserUnlocked())
        notice(R.string.dawnshell_codec_probe_requested)
        refreshHardwareCodecStatus()
    }

    override fun runSelfTest() = runHardwareCodecFileSelfTest()

    override fun runPerformanceTest() = runHardwareCodecTest(true)

    override fun showFfmpegGuide() {
        // Upstream MediaCodec spellings come first because most callers reach
        // for them before the DawnShell-specific tool names.
        showCodecGuide(
            R.string.dawnshell_codec_ffmpeg_guide_title,
            getString(R.string.dawnshell_codec_mediacodec_syntax_body) +
                getString(R.string.dawnshell_gpu_status_body) +
                getString(R.string.dawnshell_codec_ffmpeg_guide_body),
        )
    }

    override fun showLiveGuide() = showCodecGuide(
        R.string.dawnshell_codec_live_guide_title,
        getString(R.string.dawnshell_codec_live_guide_body),
    )

    private fun showCodecGuide(@StringRes title: Int, guide: String) {
        state.dialogs += BootDialog.Guide(getString(title), guide) { copyFfmpegCodecGuide(guide) }
    }

    private fun copyFfmpegCodecGuide(guide: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard == null) {
            notice(R.string.bfu_clipboard_unavailable)
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("DawnShell FFmpeg guide", guide))
        recordOperation("HARDWARE_CODEC_FFMPEG_GUIDE_COPIED")
        notice(R.string.dawnshell_codec_ffmpeg_guide_copied)
    }

    private fun runHardwareCodecFileSelfTest() {
        if (state.codecSelfTestInProgress) return
        if (!BfuPreferences.hardwareCodecBridge(this)) {
            notice(R.string.dawnshell_codec_self_test_requires_setup)
            return
        }
        state.codecSelfTestInProgress = true
        notice(R.string.dawnshell_codec_self_test_started)
        codecSelfTestExecutor.execute {
            var passed = false
            var token = -1L
            var output = "FAILED stage=debian_download"
            try {
                val layout = BfuRuntime.provision(this)
                val destination = HardwareCodecFileSelfTest.inputFile(this)
                val rootTemporary = BfuRootfsProbe.ROOTFS_PATH + "/var/tmp/dawnshell-codec-file-self-test.mp4"
                val debianScript = "set -eu; " +
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " +
                    "if ! command -v wget >/dev/null 2>&1; then " +
                    "apt-get -o Acquire::Retries=3 update; " +
                    "DEBIAN_FRONTEND=noninteractive apt-get install -y wget ca-certificates; " +
                    "fi; " +
                    "wget --timeout=30 --tries=3 -O /var/tmp/dawnshell-codec-file-self-test.mp4 " +
                    BfuSu.shellQuote(HardwareCodecFileSelfTest.TEST_URL)
                val toolbox = BfuSu.shellQuote(layout.toolboxBinary.absolutePath)
                val staged = destination.absolutePath + ".new"
                val command = BfuRuntime.guardedChroot(layout) + " " +
                    BfuSu.shellQuote(BfuRootfsProbe.ROOTFS_PATH) +
                    " /bin/sh -c " + BfuSu.shellQuote(debianScript) +
                    " && " + toolbox + " cp " + BfuSu.shellQuote(rootTemporary) +
                    " " + BfuSu.shellQuote(staged) +
                    " && " + toolbox + " chmod 0600 " + BfuSu.shellQuote(staged) +
                    " && " + toolbox + " chown " + Process.myUid() + ":" + Process.myUid() +
                    " " + BfuSu.shellQuote(staged) +
                    " && " + toolbox + " mv " + BfuSu.shellQuote(staged) +
                    " " + BfuSu.shellQuote(destination.absolutePath) +
                    " && " + toolbox + " rm -f " + BfuSu.shellQuote(rootTemporary)
                val download = BfuSu.runRaw(command, 240_000L)
                if (!download.exitedSuccessfully()) {
                    throw IOException("Debian wget failed: " + BfuSu.sanitizeTail(download.output))
                }
                token = HardwareCodecService.requestFileSelfTest(this)
                output = "FAILED token=$token timeout=true"
                val deadline = SystemClock.elapsedRealtime() + 90_000L
                while (SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(500L)
                    val status = HardwareCodecFileSelfTest.readStatus(this)
                    if (!status.contains("token=$token")) continue
                    if (status.startsWith("PASSED ") || status.startsWith("FAILED ")) {
                        output = status
                        passed = status.startsWith("PASSED ")
                        break
                    }
                }
            } catch (e: IOException) {
                output = "FAILED token=" + token + " error=" + BfuSu.sanitize(e.message)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                output = "FAILED token=$token interrupted=true"
            }
            val finalPassed = passed
            val finalOutput = output
            HardwareCodecProbe.recordRuntimeEvent(this, "SELF_TEST_$finalOutput")
            try {
                BfuOperationLog.append(this, "HARDWARE_CODEC_FILE_SELF_TEST_$finalOutput")
            } catch (e: IOException) {
                Log.w(TAG, "Could not persist file-backed codec self-test", e)
            }
            runOnUiThread {
                state.codecSelfTestInProgress = false
                refreshHardwareCodecStatus()
                notice(
                    getString(
                        if (finalPassed) R.string.dawnshell_codec_self_test_passed
                        else R.string.dawnshell_codec_self_test_failed,
                    ),
                )
            }
        }
    }

    override fun confirmLongRunStart() {
        if (!BfuPreferences.hardwareCodecBridge(this)) {
            notice(R.string.dawnshell_codec_self_test_requires_setup)
            return
        }
        confirm(
            R.string.dawnshell_codec_long_run_confirm_title, R.string.dawnshell_codec_long_run_confirm_message,
            R.string.dawnshell_codec_long_run_start,
        ) { runHardwareCodecLongRun(HardwareCodecLongRun.Operation.START) }
    }

    override fun stopLongRun() = runHardwareCodecLongRun(HardwareCodecLongRun.Operation.STOP)

    private fun runHardwareCodecLongRun(operation: HardwareCodecLongRun.Operation) {
        if (state.codecControlInProgress) return
        if (operation == HardwareCodecLongRun.Operation.START && !BfuPreferences.hardwareCodecBridge(this)) {
            notice(R.string.dawnshell_codec_self_test_requires_setup)
            return
        }
        val layout = provisionOrNull { e ->
            HardwareCodecLongRun.recordFailure(this, operation, e.message)
            notice(getString(R.string.dawnshell_codec_long_run_failed, BfuSu.sanitize(e.message)))
        } ?: return
        if (operation == HardwareCodecLongRun.Operation.START) {
            HardwareCodecService.ensureStarted(this, false)
        }
        state.codecControlInProgress = true
        notice(
            if (operation == HardwareCodecLongRun.Operation.START) R.string.dawnshell_codec_long_run_start_requested
            else R.string.dawnshell_codec_long_run_stop_requested,
        )
        codecControlExecutor.execute {
            var passed = false
            var output: String
            try {
                val result = HardwareCodecLongRun.run(this, layout, operation)
                passed = result.exitedSuccessfully()
                output = "exit=" + result.exitCode + " timeout=" + result.timedOut + " output=" + result.output
            } catch (e: IOException) {
                output = BfuSu.sanitize(e.message)
                HardwareCodecLongRun.recordFailure(this, operation, output)
            } catch (e: RuntimeException) {
                output = BfuSu.sanitize(e.message)
                HardwareCodecLongRun.recordFailure(this, operation, output)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                output = "interrupted"
                HardwareCodecLongRun.recordFailure(this, operation, output)
            }
            val successful = passed
            val summary = BfuSu.sanitize(output)
            try {
                BfuOperationLog.append(
                    this,
                    "HARDWARE_CODEC_LONG_RUN_" + operation.name + "_" +
                        (if (successful) "SUCCEEDED " else "FAILED ") + summary,
                )
            } catch (e: IOException) {
                Log.w(TAG, "Could not persist hardware codec long-run operation", e)
            }
            runOnUiThread {
                state.codecControlInProgress = false
                refreshHardwareCodecLongRunStatus()
                notice(
                    if (successful) getString(R.string.dawnshell_codec_long_run_control_succeeded)
                    else getString(R.string.dawnshell_codec_long_run_failed, summary),
                )
            }
        }
    }

    private fun runHardwareCodecTest(performance: Boolean) {
        if (state.codecSelfTestInProgress) return
        if (!BfuPreferences.hardwareCodecBridge(this)) {
            notice(R.string.dawnshell_codec_self_test_requires_setup)
            return
        }
        val testName = if (performance) "PERFORMANCE_TEST_" else "SELF_TEST_"
        val layout = provisionOrNull { e ->
            val detail = BfuSu.sanitize(e.message)
            HardwareCodecProbe.recordRuntimeEvent(this, testName + "FAILED runtime_provisioning " + detail)
            recordOperation("HARDWARE_CODEC_" + testName + "FAILED runtime_provisioning " + detail)
            notice(
                getString(
                    if (performance) R.string.dawnshell_codec_performance_test_failed
                    else R.string.dawnshell_codec_self_test_failed,
                ),
            )
            refreshHardwareCodecStatus()
        } ?: return
        val chrootCommand = BfuRuntime.guardedChroot(layout)
        state.codecSelfTestInProgress = true
        notice(
            if (performance) R.string.dawnshell_codec_performance_test_started
            else R.string.dawnshell_codec_self_test_started,
        )
        HardwareCodecService.ensureStarted(this, false)
        codecSelfTestExecutor.execute {
            var output: String
            var passed = false
            var missingTools = false
            try {
                Thread.sleep(500L)
                val command = chrootCommand + " " + BfuSu.shellQuote(BfuRootfsProbe.ROOTFS_PATH) +
                    (if (performance) " /usr/local/bin/dawnshell-codec-performance-test"
                    else " /usr/local/bin/dawnshell-codec-self-test")
                val result = if (performance) BfuSu.runRaw(command, 600_000L) else BfuSu.run(command, 30_000L)
                output = "command=" + result.command + " exit=" + result.exitCode +
                    " timeout=" + result.timedOut + " output=" + result.output
                passed = result.exitedSuccessfully()
                // Exit 127 means the configurator never installed the codec
                // tools, which is a setup step rather than a codec failure.
                missingTools = !passed && result.exitCode == 127 &&
                    result.output != null && result.output.contains("dawnshell-codec")
            } catch (e: InterruptedException) {
                passed = false
                Thread.currentThread().interrupt()
                output = "interrupted"
            } catch (e: RuntimeException) {
                passed = false
                output = BfuSu.sanitize(e.message)
            }
            val finalPassed = passed
            val finalMissingTools = missingTools
            val finalOutput = if (performance) BfuSu.sanitizeTail(output) else BfuSu.sanitize(output)
            HardwareCodecProbe.recordRuntimeEvent(
                this,
                testName + (if (finalPassed) "PASSED "
                else if (finalMissingTools) "FAILED tools_missing run_debian_configuration "
                else "FAILED ") + finalOutput,
            )
            try {
                BfuOperationLog.append(
                    this,
                    "HARDWARE_CODEC_" + testName + (if (finalPassed) "PASSED " else "FAILED ") + finalOutput,
                )
            } catch (e: IOException) {
                Log.w(TAG, "Could not persist hardware codec self-test operation", e)
            }
            runOnUiThread {
                state.codecSelfTestInProgress = false
                refreshHardwareCodecStatus()
                val passedText = if (performance) R.string.dawnshell_codec_performance_test_passed
                else R.string.dawnshell_codec_self_test_passed
                var failedText = if (performance) R.string.dawnshell_codec_performance_test_failed
                else R.string.dawnshell_codec_self_test_failed
                if (finalMissingTools) failedText = R.string.dawnshell_codec_tools_missing
                // The notice reports the outcome only; the full codec output
                // stays in the hardware codec log, where it can be copied.
                notice(getString(if (finalPassed) passedText else failedText))
            }
        }
    }

    // Debian lifecycle

    override fun requestLifecycle(operation: DebianLauncher.Operation) {
        val name = operation.name.lowercase(Locale.US)
        if ((operation == DebianLauncher.Operation.START || operation == DebianLauncher.Operation.RESTART) &&
            !BfuPreferences.isEnabled(this)
        ) {
            recordOperation("DEBIAN_LIFECYCLE_REJECTED operation=$name bfu_disabled=true")
            notice(R.string.bfu_install_requires_enabled)
            return
        }
        tryProvision(
            block = {
                BfuCeIsolationProbe.provisionSentinel(this)
                BfuRuntime.provision(this)
                BfuBootService.requestDebianLifecycle(this, operation)
                recordOperation("DEBIAN_LIFECYCLE_REQUESTED operation=$name")
                notice(getString(R.string.bfu_lifecycle_requested, operation.name))
                refreshLifecycleStatus()
            },
            onFailure = { e ->
                recordOperation("DEBIAN_LIFECYCLE_REQUEST_FAILED operation=" + operation.name + " " + BfuSu.sanitize(e.message))
                notice(getString(R.string.bfu_lifecycle_request_failed, e.message))
            },
        )
    }

    override fun confirmStop() = confirm(
        R.string.bfu_stop_confirm_title, R.string.bfu_stop_confirm_message, R.string.bfu_stop_confirm_button,
    ) { requestLifecycle(DebianLauncher.Operation.STOP) }

    override fun confirmForceStop() = confirm(
        R.string.bfu_force_stop_confirm_title, R.string.bfu_force_stop_confirm_message,
        R.string.bfu_force_stop_confirm_button,
    ) { requestLifecycle(DebianLauncher.Operation.FORCE_STOP) }

    override fun confirmRestart() = confirm(
        R.string.bfu_restart_confirm_title, R.string.bfu_restart_confirm_message, R.string.bfu_restart_confirm_button,
    ) { requestLifecycle(DebianLauncher.Operation.RESTART) }

    // Account passwords

    override fun updatePassword(account: String) {
        if (account == "root") {
            updateDebianPassword(account, state.rootPassword, state.rootPasswordConfirm)
        } else {
            updateDebianPassword(account, state.debianPassword, state.debianPasswordConfirm)
        }
    }

    private fun updateDebianPassword(account: String, passwordEditor: TextFieldState, confirmationEditor: TextFieldState) {
        if (!isUserUnlocked()) {
            notice(R.string.bfu_password_requires_unlock)
            recordOperation("DEBIAN_PASSWORD_REJECTED account=$account user_locked=true")
            return
        }
        if (state.passwordUpdateInProgress) {
            notice(R.string.bfu_password_busy)
            return
        }

        val password = CharArray(passwordEditor.text.length) { passwordEditor.text[it] }
        val confirmation = CharArray(confirmationEditor.text.length) { confirmationEditor.text[it] }
        passwordEditor.clearText()
        confirmationEditor.clearText()
        if (!password.contentEquals(confirmation)) {
            password.fill(Char.MIN_VALUE)
            confirmation.fill(Char.MIN_VALUE)
            notice(R.string.bfu_password_mismatch)
            recordOperation("DEBIAN_PASSWORD_REJECTED account=$account confirmation_mismatch=true")
            return
        }
        confirmation.fill(Char.MIN_VALUE)

        state.passwordUpdateInProgress = true
        val applicationContext = applicationContext
        passwordExecutor.execute {
            var result: DebianPasswordManager.Result? = null
            var failure: String? = null
            try {
                result = DebianPasswordManager.update(applicationContext, account, password)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                failure = "interrupted"
                password.fill(Char.MIN_VALUE)
            } catch (e: RuntimeException) {
                failure = BfuSu.sanitize(e.message)
                password.fill(Char.MIN_VALUE)
            }
            val finalResult = result
            val finalFailure = failure
            runOnUiThread { finishPasswordUpdate(account, finalResult, finalFailure) }
        }
    }

    private fun finishPasswordUpdate(account: String, result: DebianPasswordManager.Result?, failure: String?) {
        state.passwordUpdateInProgress = false
        if (result != null && result.succeeded()) {
            recordOperation("DEBIAN_PASSWORD_UPDATED account=$account command=" + result.command + " exit=0 timeout=false")
            notice(getString(R.string.bfu_password_updated, account))
        } else {
            val safeFailure = if (result == null) failure ?: "unknown failure" else result.summary()
            recordOperation("DEBIAN_PASSWORD_FAILED account=$account $safeFailure")
            notice(getString(R.string.bfu_password_failed, account, safeFailure))
        }
    }

    // Status lines

    private inline fun loadStatus(read: () -> OperationStatus?): StatusLoad = try {
        read()?.let(StatusLoad::Loaded) ?: StatusLoad.Missing
    } catch (e: IOException) {
        StatusLoad.Failed(BfuSu.sanitize(e.message))
    } catch (e: RuntimeException) {
        StatusLoad.Failed(BfuSu.sanitize(e.message))
    }

    private fun refreshSystemConfigurationStatus() {
        state.systemConfigStatus = loadStatus { DebianSystemProvisioner.readOperationStatus(this) }
    }

    private fun refreshLifecycleStatus() {
        state.lifecycleStatus = loadStatus { DebianLauncher.readOperationStatus(this) }
    }

    private fun refreshDockerPolicyStatus() {
        state.dockerPolicyStatus = loadStatus { DockerNetworkProvisioner.readOperationStatus(this) }
    }

    private fun refreshHardwareCodecStatus() {
        state.hardwareCodecStatus = loadStatus { HardwareCodecProbe.readOperationStatus(this) }
    }

    private fun refreshHardwareCodecLongRunStatus() {
        state.hardwareCodecLongRunStatus = loadStatus { HardwareCodecLongRun.readOperationStatus(this) }
    }

    private fun refreshInstallerStatus() {
        state.installStatus = loadStatus { DebianRootfsInstaller.readOperationStatus(this) }
    }

    private fun recordOperation(message: String) {
        try {
            BfuOperationLog.append(this, message)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to append BFU UI operation log", e)
        }
    }

    private fun oneLine(value: String?): String =
        if (value == null) "(null)" else value.replace('\r', ' ').replace('\n', ' ').trim()

    private fun compact(value: String?, maximumLength: Int): String {
        val clean = value?.trim() ?: return ""
        return if (clean.length <= maximumLength) clean else clean.substring(0, maxOf(0, maximumLength - 1)) + "…"
    }

    private fun isUserUnlocked(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true
        val userManager = getSystemService(USER_SERVICE) as UserManager?
        return userManager != null && userManager.isUserUnlocked
    }

    private companion object {
        const val TAG = "DawnShell"
    }
}
