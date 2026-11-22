@file:JvmName("BootScreenKt")
@file:JvmMultifileClass

package me.aroxu.dawnshell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.aroxu.dawnshell.ui.*

internal interface BootActions {
    fun openLogs()
    fun openLog(type: String)
    fun openLicenses()
    fun confirmApplySettings()
    fun confirmRootAuthorization()
    fun refreshProbes()
    fun confirmDebianInstall()
    fun confirmSystemConfiguration()
    fun requestLifecycle(operation: DebianLauncher.Operation)
    fun confirmRestart()
    fun confirmStop()
    fun confirmForceStop()
    fun confirmExportPrivateKey()
    fun confirmCopyKeyImportCommand()
    fun buildSshConnectCommand(): String
    fun copySshConnectCommand()
    fun confirmRotateSshKey()
    fun updatePassword(account: String)
    fun probeHardwareCodecs()
    fun runSelfTest()
    fun runPerformanceTest()
    fun showFfmpegGuide()
    fun showLiveGuide()
    fun confirmLongRunStart()
    fun stopLongRun()
    fun confirmRootfsRemoval()
}

@Composable
internal fun SetupPage(s: BootUiState, a: BootActions) {
    val colors = MaterialTheme.colorScheme
    val logIcon = painterResource(R.drawable.ic_logs_24)

    DawnShellCard(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        containerColor = colors.primaryContainer,
        border = null,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.dawnshell_home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Body(stringResource(R.string.bfu_settings_explanation), Modifier.padding(top = 8.dp), colors.onPrimaryContainer)
            DawnShellButton(
                stringResource(R.string.dawnshell_open_live_logs), a::openLogs,
                Modifier.padding(top = 16.dp), contentColor = colors.onPrimaryContainer, icon = logIcon,
            )
        }
    }
    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_direct_boot))
        Body(
            stringResource(R.string.dawnshell_section_direct_boot_description),
            Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        SwitchRow(stringResource(R.string.bfu_enable), s.enableBfu, { s.enableBfu = it })
        Small(s.rootAuthorizationStatus, Modifier.padding(top = 12.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_request_root_authorization), a::confirmRootAuthorization,
            Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !s.rootAuthorizationInProgress,
        )
    }
    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_debian_setup))
        Body(stringResource(R.string.dawnshell_section_debian_setup_description), Modifier.padding(top = 4.dp))
        Small(
            s.installStatus.text(
                R.string.bfu_debian_install_status_none, R.string.bfu_debian_install_status, R.string.bfu_debian_install_status_failed, 360,
            ),
            Modifier.padding(top = 16.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_install_debian), a::confirmDebianInstall,
            Modifier.fillMaxWidth().padding(top = 10.dp), filled = true,
        )
        Small(
            s.systemConfigStatus.text(
                R.string.bfu_system_config_status_none, R.string.bfu_system_config_status, R.string.bfu_system_config_status_failed, 360,
            ),
            Modifier.padding(top = 16.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_configure_system), a::confirmSystemConfiguration,
            Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_view_setup_logs), a::openLogs,
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }
}

@Composable
internal fun AccountPage(s: BootUiState, a: BootActions) {
    Body(stringResource(R.string.dawnshell_section_accounts_description), Modifier.padding(top = 4.dp, bottom = 14.dp))
    PasswordFields(
        s.rootPassword, s.rootPasswordConfirm,
        R.string.bfu_root_password_hint, R.string.bfu_root_password_confirm_hint,
        R.string.bfu_set_root_password, !s.passwordUpdateInProgress,
    ) { a.updatePassword("root") }
    Divider(top = 20, bottom = 20)
    PasswordFields(
        s.debianPassword, s.debianPasswordConfirm,
        R.string.bfu_debian_password_hint, R.string.bfu_debian_password_confirm_hint,
        R.string.bfu_set_debian_password, !s.passwordUpdateInProgress,
    ) { a.updatePassword("debian") }
}

@Composable
private fun PasswordFields(
    password: TextFieldState,
    confirmation: TextFieldState,
    hint: Int,
    confirmationHint: Int,
    buttonText: Int,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    PasswordField(password, stringResource(hint))
    PasswordField(confirmation, stringResource(confirmationHint), Modifier.padding(top = 8.dp))
    DawnShellButton(
        stringResource(buttonText), onSubmit, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = enabled,
    )
}

@Composable
private fun PasswordField(state: TextFieldState, label: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
internal fun UsbPage(s: BootUiState, a: BootActions) {
    Body(stringResource(R.string.dawnshell_host_usb_description), Modifier.padding(top = 4.dp))

    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val options = listOf(
            Triple(BfuPreferences.USB_PASSTHROUGH_OFF, "Off", stringResource(R.string.dawnshell_host_usb_off)),
            Triple(BfuPreferences.USB_PASSTHROUGH_DIRECT, "Direct", stringResource(R.string.dawnshell_host_usb_direct)),
            Triple(BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE, "Exclusive passthrough", stringResource(R.string.dawnshell_host_usb_exclusive)),
        )

        options.forEachIndexed { i, (usbMode, title, description) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(i, options.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                leadingContent = {
                    RadioButton(
                        selected = s.usbMode == usbMode,
                        onClick = { s.selectUsbMode(usbMode) },
                    )
                },
                content = { Text(title) },
                supportingContent = { Text(description) },
                onClick = { s.selectUsbMode(usbMode) },
            )
        }
    }
    AnimatedVisibility(
        visible = s.usbMode == BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE,
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        Callout(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                state = s.usbDeviceIds,
                modifier = Modifier.fillMaxWidth(),
                isError = s.usbDeviceIdsError != null,
                label = { Text(stringResource(R.string.dawnshell_usb_exclusive_ids_hint)) },
                supportingText = s.usbDeviceIdsError?.let { error -> { Text(error) } },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Small(
                stringResource(R.string.dawnshell_host_usb_warning), Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    Callout(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        Modifier.padding(top = 12.dp)
    ) {
        Small(
            stringResource(R.string.dawnshell_host_usb_scope_explanation),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
    DawnShellButton(
        stringResource(R.string.dawnshell_view_host_usb_log), { a.openLog(DawnShellLogRepository.LIFECYCLE) },
        Modifier.fillMaxWidth().padding(top = 12.dp), icon = painterResource(R.drawable.ic_logs_24),
    )
}

@Composable
internal fun CodecPage(s: BootUiState, a: BootActions) {
    Body(stringResource(R.string.dawnshell_codec_section_description), Modifier.padding(top = 4.dp))
    SwitchRow(
        stringResource(R.string.dawnshell_codec_enable), s.hardwareCodec, { s.hardwareCodec = it },
        Modifier.padding(top = 12.dp),
    )

    Callout(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        Modifier.padding(top = 8.dp)
    ) {
        Small(stringResource(R.string.dawnshell_codec_scope), color = MaterialTheme.colorScheme.onSecondaryContainer)
    }

    Divider()

    SubTitle(stringResource(R.string.dawnshell_codec_apply_probe))

    // TODO: Rich
    SelectableSmall(
        s.hardwareCodecStatus.text(
            R.string.dawnshell_codec_status_none, R.string.dawnshell_codec_status, R.string.dawnshell_codec_status_failed, 520,
        ),
        Modifier.padding(top = 12.dp))
    DawnShellButton(
        stringResource(R.string.dawnshell_codec_apply_probe), a::probeHardwareCodecs,
        Modifier.fillMaxWidth().padding(top = 12.dp), filled = true,
    )

    Divider()

    SubTitle("Tests")
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, 2),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = {
                Text(stringResource(R.string.dawnshell_codec_run_self_test))
            },
            supportingContent = {},
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = a::runSelfTest,
            enabled = !s.codecSelfTestInProgress,
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(1, 2),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = {
                Text(stringResource(R.string.dawnshell_codec_run_performance_test))
            },
            supportingContent = {},
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = a::runPerformanceTest,
            enabled = !s.codecSelfTestInProgress,
        )
    }

    Divider()
    SubTitle(stringResource(R.string.dawnshell_codec_long_run_title))
    Small(stringResource(R.string.dawnshell_codec_long_run_description), Modifier.padding(top = 4.dp))
    Callout(colorResource(R.color.ds_warning_container), colorResource(R.color.ds_on_warning_container), Modifier.padding(top = 8.dp)) {
        Small(stringResource(R.string.dawnshell_codec_long_run_warning), color = colorResource(R.color.ds_on_warning_container))
    }
    SelectableSmall(
        s.hardwareCodecLongRunStatus.text(
            R.string.dawnshell_codec_long_run_status_none, R.string.dawnshell_codec_long_run_status,
            R.string.dawnshell_codec_long_run_status_failed, 520,
        ),
        Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_long_run_start), a::confirmLongRunStart,
            Modifier.weight(1f), filled = true, enabled = !s.codecControlInProgress,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_long_run_stop), a::stopLongRun,
            Modifier.weight(1f), enabled = s.codecControlInProgress,
        )
    }
    DawnShellButton(
        stringResource(R.string.dawnshell_codec_long_run_view_log),
        { a.openLog(DawnShellLogRepository.CODEC_LONG_RUN) },
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), icon = painterResource(R.drawable.ic_logs_24),
    )

    SectionCard {
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_ffmpeg_guide_button), a::showFfmpegGuide,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_live_guide_button), a::showLiveGuide,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_view_log), { a.openLog(DawnShellLogRepository.HARDWARE_CODEC) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = painterResource(R.drawable.ic_logs_24),
        )
    }
}

@Composable
internal fun CompatibilityPage(s: BootUiState, a: BootActions) {
    Body(stringResource(R.string.dawnshell_section_compatibility_description), Modifier.padding(top = 4.dp))

    SubTitle(stringResource(R.string.dawnshell_cgroup_policy_title), Modifier.padding(top = 18.dp))
    Callout(colorResource(R.color.ds_warning_container), colorResource(R.color.ds_on_warning_container), Modifier.padding(top = 8.dp)) {
        Small(stringResource(R.string.dawnshell_cgroup_policy_warning), color = colorResource(R.color.ds_on_warning_container))
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val options = listOf(
            Triple(BfuPreferences.CGROUP_AUTO, "Automatic", stringResource(R.string.dawnshell_cgroup_policy_auto)),
            Triple(BfuPreferences.CGROUP_V2, "Force cgroup v2 only (experimental)", stringResource(R.string.dawnshell_cgroup_policy_v2)),
            Triple(BfuPreferences.CGROUP_V1, "Force cgroup v1 only", stringResource(R.string.dawnshell_cgroup_policy_v1)),
        )

        options.forEachIndexed { i, (cgroupPolicy, title, description) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(i, options.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                leadingContent = {
                    RadioButton(
                        selected = s.cgroupPolicy == cgroupPolicy,
                        onClick = { s.cgroupPolicy = cgroupPolicy },
                    )
                },
                content = { Text(title) },
                supportingContent = { Text(description) },
                onClick = { s.cgroupPolicy = cgroupPolicy },
            )
        }
    }
    SwitchRow(
        stringResource(R.string.dawnshell_pid_namespace_fallback), s.pidNamespaceFallback,
        { s.pidNamespaceFallback = it }, Modifier.padding(top = 12.dp),
    )
    Small(
        stringResource(R.string.dawnshell_pid_namespace_fallback_warning), Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.error,
    )

    Divider()

    SubTitle(stringResource(R.string.dawnshell_docker_policy_title))
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val options = listOf(
            Triple(BfuPreferences.DOCKER_HOST_ONLY, "Host-only", stringResource(R.string.dawnshell_docker_policy_host)),
            Triple(BfuPreferences.DOCKER_AUTO_BRIDGE, "Automatic", stringResource(R.string.dawnshell_docker_policy_auto)),
            Triple(BfuPreferences.DOCKER_NATIVE_NFT_BRIDGE, "Force native nftables (experimental)", stringResource(R.string.dawnshell_docker_policy_nft)),
            Triple(BfuPreferences.DOCKER_IPTABLES_NFT_BRIDGE, "Force iptables-nft bridge", stringResource(R.string.dawnshell_docker_policy_iptables_nft)),
            Triple(BfuPreferences.DOCKER_LEGACY_BRIDGE, "Force legacy iptables bridge", stringResource(R.string.dawnshell_docker_policy_legacy)),
        )

        options.forEachIndexed { i, (dockerPolicy, title, description) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(i, options.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                leadingContent = {
                    RadioButton(
                        selected = s.dockerPolicy == dockerPolicy,
                        onClick = { s.dockerPolicy = dockerPolicy },
                    )
                },
                content = { Text(title) },
                supportingContent = { Text(description) },
                onClick = { s.dockerPolicy = dockerPolicy },
            )
        }
    }
    SwitchRow(
        stringResource(R.string.dawnshell_docker_host_ipc_compatibility), s.dockerHostIpc,
        { s.dockerHostIpc = it }, Modifier.padding(top = 10.dp),
    )
    Small(
        stringResource(R.string.dawnshell_docker_host_ipc_warning), Modifier.padding(top = 4.dp),
        color = MaterialTheme.colorScheme.error,
    )
    Callout(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.padding(top = 8.dp)) {
        Small(stringResource(R.string.dawnshell_docker_policy_warning), color = MaterialTheme.colorScheme.onErrorContainer)
    }

    Divider()

    SubTitle(stringResource(R.string.dawnshell_docker_storage_title))
    Small(stringResource(R.string.dawnshell_docker_storage_description), Modifier.padding(top = 4.dp))

    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val options = listOf(
            Triple(BfuPreferences.DOCKER_STORAGE_OVERLAY2, "OverlayFS (overlay2)", stringResource(R.string.dawnshell_docker_storage_overlay2)),
            Triple(BfuPreferences.DOCKER_STORAGE_VFS, "VFS", stringResource(R.string.dawnshell_docker_storage_vfs)),
        )

        options.forEachIndexed { i, (dockerStorage, title, description) ->
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(i, options.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                leadingContent = {
                    RadioButton(
                        selected = s.dockerStorage == dockerStorage,
                        onClick = { s.dockerStorage = dockerStorage },
                    )
                },
                content = { Text(title) },
                supportingContent = { Text(description) },
                onClick = { s.dockerStorage = dockerStorage },
            )
        }
    }
    Small(
        stringResource(R.string.dawnshell_docker_storage_warning), Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.error,
    )

    DawnShellButton(
        stringResource(R.string.dawnshell_view_compatibility_log),
        { a.openLog(DawnShellLogRepository.COMPATIBILITY) },
        Modifier.fillMaxWidth().padding(top = 18.dp), icon = painterResource(R.drawable.ic_logs_24),
    )
}

@Composable
internal fun DiagnosticsPage(s: BootUiState, a: BootActions) {
    Body(stringResource(R.string.dawnshell_section_diagnostics_description), Modifier.padding(top = 4.dp))

    Small(s.probeSummary, Modifier.padding(top = 14.dp, start = 4.dp), maxLines = 4)
    DawnShellButton(
        stringResource(R.string.bfu_refresh_probe_status), a::refreshProbes,
        Modifier.fillMaxWidth().padding(top = 10.dp),
    )

    val warningContainer = colorResource(R.color.ds_warning_container)
    val onWarningContainer = colorResource(R.color.ds_on_warning_container)
    Callout(warningContainer, onWarningContainer, Modifier.padding(top = 12.dp)) {
        SwitchRow(
            stringResource(R.string.bfu_allow_ce_readable), s.allowCeReadableBfu,
            { s.allowCeReadableBfu = it }, contentColor = onWarningContainer,
        )
        Small(
            stringResource(R.string.bfu_allow_ce_readable_warning), Modifier.padding(top = 4.dp),
            color = onWarningContainer,
        )
    }
}
