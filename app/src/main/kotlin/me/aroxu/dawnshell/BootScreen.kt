package me.aroxu.dawnshell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.drop
import me.aroxu.dawnshell.ui.Callout
import me.aroxu.dawnshell.ui.DawnShellButton
import me.aroxu.dawnshell.ui.DawnShellCard
import me.aroxu.dawnshell.ui.DawnShellScaffold
import me.aroxu.dawnshell.ui.RadioGroup
import me.aroxu.dawnshell.ui.SwitchRow
import me.aroxu.dawnshell.ui.TerminalPanel
import me.aroxu.dawnshell.ui.consoleTextStyle

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

private enum class DashboardPage { HOME, ACCESS, ADVANCED }

@Composable
internal fun BootScreen(state: BootUiState, actions: BootActions, onDiscard: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(DashboardPage.HOME) }
    val scroll = rememberScrollState()
    var menuOpen by remember { mutableStateOf(false) }
    val discardTitle = stringResource(R.string.dawnshell_discard_changes_title)
    val discardMessage = stringResource(R.string.dawnshell_discard_changes_message)
    val discardLabel = stringResource(R.string.dawnshell_discard_changes)

    LaunchedEffect(page) { scroll.scrollTo(0) }
    LaunchedEffect(state) {
        snapshotFlow { state.usbDeviceIds.text.toString() }.drop(1).collect { state.usbDeviceIdsError = null }
    }
    BackHandler(enabled = page == DashboardPage.HOME && state.settingsDirty) {
        state.dialogs += BootDialog.Confirm(discardTitle, discardMessage, discardLabel, onDiscard)
    }
    BackHandler(enabled = page != DashboardPage.HOME) { page = DashboardPage.HOME }

    DawnShellScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = actions::openLogs) {
                Icon(painterResource(R.drawable.ic_logs_24), stringResource(R.string.dawnshell_logs))
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert_24),
                    stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dawnshell_open_source_licenses)) },
                    onClick = {
                        menuOpen = false
                        actions.openLicenses()
                    },
                )
            }
        },
        bottomBar = {
            Column {
                if (state.settingsDirty) ApplyBar(actions)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationItem(page == DashboardPage.HOME, { page = DashboardPage.HOME },
                        R.drawable.ic_home_24, R.string.dawnshell_navigation_home)
                    NavigationItem(page == DashboardPage.ACCESS, { page = DashboardPage.ACCESS },
                        R.drawable.ic_key_24, R.string.dawnshell_navigation_access)
                    NavigationItem(page == DashboardPage.ADVANCED, { page = DashboardPage.ADVANCED },
                        R.drawable.ic_tune_24, R.string.dawnshell_navigation_advanced)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            when (page) {
                DashboardPage.HOME -> HomePage(state, actions)
                DashboardPage.ACCESS -> AccessPage(state, actions)
                DashboardPage.ADVANCED -> AdvancedPage(state, actions)
            }
        }
    }
    BootDialogHost(state)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Int,
    label: Int,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(icon), contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun ApplyBar(actions: BootActions) {
    Card(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                stringResource(R.string.dawnshell_unsaved_settings),
                Modifier.weight(1f).padding(start = 4.dp, end = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            DawnShellButton(
                stringResource(R.string.dawnshell_apply_changes),
                actions::confirmApplySettings,
                Modifier,
                filled = true,
            )
        }
    }
}

@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit,
) {
    DawnShellCard(modifier.fillMaxWidth().padding(bottom = 16.dp), containerColor = containerColor) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String, color: Color = Color.Unspecified) =
    Text(text, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun SubTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Body(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, color = color, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Small(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier, color = color, style = MaterialTheme.typography.bodySmall,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Divider(top: Int = 20, bottom: Int = 18) =
    HorizontalDivider(Modifier.padding(top = top.dp, bottom = bottom.dp))

@Composable
private fun SelectableSmall(text: String, modifier: Modifier = Modifier) =
    SelectionContainer { Small(text, modifier) }

@Composable
private fun HomePage(s: BootUiState, a: BootActions) {
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
        Small(s.installStatus, Modifier.padding(top = 16.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_install_debian), a::confirmDebianInstall,
            Modifier.fillMaxWidth().padding(top = 10.dp), filled = true,
        )
        Small(s.systemConfigStatus, Modifier.padding(top = 16.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_configure_system), a::confirmSystemConfiguration,
            Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_view_setup_logs), a::openLogs,
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }
    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_runtime))
        Small(s.lifecycleStatus, Modifier.padding(top = 10.dp, start = 4.dp), maxLines = 5)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DawnShellButton(
                stringResource(R.string.bfu_start_debian_short),
                { a.requestLifecycle(DebianLauncher.Operation.START) }, Modifier.weight(1f), filled = true,
            )
            DawnShellButton(stringResource(R.string.bfu_restart_debian_short), a::confirmRestart, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DawnShellButton(
                stringResource(R.string.bfu_status_debian_short),
                { a.requestLifecycle(DebianLauncher.Operation.STATUS) }, Modifier.weight(1f),
            )
            DawnShellButton(stringResource(R.string.bfu_stop_debian_short), a::confirmStop, Modifier.weight(1f))
        }
        DawnShellButton(
            stringResource(R.string.bfu_force_stop_debian_short), a::confirmForceStop,
            Modifier.fillMaxWidth().padding(top = 8.dp), contentColor = colors.error,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_view_runtime_log), { a.openLog(DawnShellLogRepository.LIFECYCLE) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }
}

@Composable
private fun AccessPage(s: BootUiState, a: BootActions) {
    SectionCard(Modifier.padding(top = 8.dp)) {
        SectionTitle(stringResource(R.string.dawnshell_section_access))
        Body(stringResource(R.string.dawnshell_section_access_description), Modifier.padding(top = 4.dp))
        TerminalPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            SelectionContainer {
                Text(s.generatedPublicKey, Modifier.padding(14.dp), style = consoleTextStyle())
            }
        }
        DawnShellButton(
            stringResource(R.string.bfu_export_private_key_file), a::confirmExportPrivateKey,
            Modifier.fillMaxWidth().padding(top = 12.dp), filled = true,
        )
        DawnShellButton(
            stringResource(R.string.bfu_copy_key_import_command), a::confirmCopyKeyImportCommand,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        DawnShellButton(
            stringResource(R.string.bfu_copy_ssh_connect_command), a::copySshConnectCommand,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        DawnShellButton(
            stringResource(R.string.bfu_rotate_ssh_client_key), a::confirmRotateSshKey,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_accounts))
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
private fun AdvancedPage(s: BootUiState, a: BootActions) {
    val colors = MaterialTheme.colorScheme
    val warningContainer = colorResource(R.color.ds_warning_container)
    val onWarningContainer = colorResource(R.color.ds_on_warning_container)
    val logIcon = painterResource(R.drawable.ic_logs_24)
    val exclusive = s.usbMode == BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE

    SectionCard(Modifier.padding(top = 8.dp)) {
        SectionTitle(stringResource(R.string.dawnshell_host_usb_title))
        Body(stringResource(R.string.dawnshell_host_usb_description), Modifier.padding(top = 4.dp))
        Callout(colors.secondaryContainer, colors.onSecondaryContainer, Modifier.padding(top = 12.dp)) {
            Small(stringResource(R.string.dawnshell_host_usb_scope_explanation), color = colors.onSecondaryContainer)
        }
        Callout(colors.errorContainer, colors.onErrorContainer, Modifier.padding(top = 12.dp)) {
            RadioGroup(
                listOf(
                    BfuPreferences.USB_PASSTHROUGH_OFF to stringResource(R.string.dawnshell_host_usb_off),
                    BfuPreferences.USB_PASSTHROUGH_DIRECT to stringResource(R.string.dawnshell_host_usb_direct),
                    BfuPreferences.USB_PASSTHROUGH_EXCLUSIVE to stringResource(R.string.dawnshell_host_usb_exclusive),
                ),
                s.usbMode, s::selectUsbMode, contentColor = colors.onErrorContainer,
            )
            OutlinedTextField(
                state = s.usbDeviceIds,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = exclusive,
                isError = s.usbDeviceIdsError != null,
                label = { Text(stringResource(R.string.dawnshell_usb_exclusive_ids_hint)) },
                supportingText = s.usbDeviceIdsError?.let { error -> { Text(error) } },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
            Small(
                stringResource(R.string.dawnshell_host_usb_warning), Modifier.padding(top = 4.dp),
                color = colors.onErrorContainer,
            )
        }
        DawnShellButton(
            stringResource(R.string.dawnshell_view_host_usb_log), { a.openLog(DawnShellLogRepository.LIFECYCLE) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }

    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_codec_section_title))
        Body(stringResource(R.string.dawnshell_codec_section_description), Modifier.padding(top = 4.dp))
        SwitchRow(
            stringResource(R.string.dawnshell_codec_enable), s.hardwareCodec, { s.hardwareCodec = it },
            Modifier.padding(top = 12.dp),
        )
        Callout(colors.secondaryContainer, colors.onSecondaryContainer, Modifier.padding(top = 8.dp)) {
            Small(stringResource(R.string.dawnshell_codec_scope), color = colors.onSecondaryContainer)
        }
        SelectableSmall(s.hardwareCodecStatus, Modifier.padding(top = 12.dp))
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_apply_probe), a::probeHardwareCodecs,
            Modifier.fillMaxWidth().padding(top = 12.dp), filled = true,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_run_self_test), a::runSelfTest,
            Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !s.codecSelfTestInProgress,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_run_performance_test), a::runPerformanceTest,
            Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !s.codecSelfTestInProgress,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_ffmpeg_guide_button), a::showFfmpegGuide,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_live_guide_button), a::showLiveGuide,
            Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Divider()
        SubTitle(stringResource(R.string.dawnshell_codec_long_run_title))
        Small(stringResource(R.string.dawnshell_codec_long_run_description), Modifier.padding(top = 4.dp))
        Callout(warningContainer, onWarningContainer, Modifier.padding(top = 8.dp)) {
            Small(stringResource(R.string.dawnshell_codec_long_run_warning), color = onWarningContainer)
        }
        SelectableSmall(s.hardwareCodecLongRunStatus, Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DawnShellButton(
                stringResource(R.string.dawnshell_codec_long_run_start), a::confirmLongRunStart,
                Modifier.weight(1f), filled = true, enabled = !s.codecControlInProgress,
            )
            DawnShellButton(
                stringResource(R.string.dawnshell_codec_long_run_stop), a::stopLongRun,
                Modifier.weight(1f), enabled = !s.codecControlInProgress,
            )
        }
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_long_run_view_log),
            { a.openLog(DawnShellLogRepository.CODEC_LONG_RUN) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
        DawnShellButton(
            stringResource(R.string.dawnshell_codec_view_log), { a.openLog(DawnShellLogRepository.HARDWARE_CODEC) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }

    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_compatibility))
        Body(stringResource(R.string.dawnshell_section_compatibility_description), Modifier.padding(top = 4.dp))
        SubTitle(stringResource(R.string.dawnshell_cgroup_policy_title), Modifier.padding(top = 18.dp))
        RadioGroup(
            listOf(
                BfuPreferences.CGROUP_AUTO to stringResource(R.string.dawnshell_cgroup_policy_auto),
                BfuPreferences.CGROUP_V2 to stringResource(R.string.dawnshell_cgroup_policy_v2),
                BfuPreferences.CGROUP_V1 to stringResource(R.string.dawnshell_cgroup_policy_v1),
            ),
            s.cgroupPolicy, { s.cgroupPolicy = it }, Modifier.padding(top = 6.dp),
        )
        Callout(warningContainer, onWarningContainer, Modifier.padding(top = 8.dp)) {
            Small(stringResource(R.string.dawnshell_cgroup_policy_warning), color = onWarningContainer)
        }
        SwitchRow(
            stringResource(R.string.dawnshell_pid_namespace_fallback), s.pidNamespaceFallback,
            { s.pidNamespaceFallback = it }, Modifier.padding(top = 12.dp),
        )
        Small(
            stringResource(R.string.dawnshell_pid_namespace_fallback_warning), Modifier.padding(top = 4.dp),
            color = colors.error,
        )
        Divider()
        SubTitle(stringResource(R.string.dawnshell_docker_policy_title))
        RadioGroup(
            listOf(
                BfuPreferences.DOCKER_HOST_ONLY to stringResource(R.string.dawnshell_docker_policy_host),
                BfuPreferences.DOCKER_AUTO_BRIDGE to stringResource(R.string.dawnshell_docker_policy_auto),
                BfuPreferences.DOCKER_NATIVE_NFT_BRIDGE to stringResource(R.string.dawnshell_docker_policy_nft),
                BfuPreferences.DOCKER_IPTABLES_NFT_BRIDGE to stringResource(R.string.dawnshell_docker_policy_iptables_nft),
                BfuPreferences.DOCKER_LEGACY_BRIDGE to stringResource(R.string.dawnshell_docker_policy_legacy),
            ),
            s.dockerPolicy, { s.dockerPolicy = it }, Modifier.padding(top = 6.dp),
        )
        SwitchRow(
            stringResource(R.string.dawnshell_docker_host_ipc_compatibility), s.dockerHostIpc,
            { s.dockerHostIpc = it }, Modifier.padding(top = 10.dp),
        )
        Small(
            stringResource(R.string.dawnshell_docker_host_ipc_warning), Modifier.padding(top = 4.dp),
            color = colors.error,
        )
        SubTitle(stringResource(R.string.dawnshell_docker_storage_title), Modifier.padding(top = 16.dp))
        Small(stringResource(R.string.dawnshell_docker_storage_description), Modifier.padding(top = 4.dp))
        RadioGroup(
            listOf(
                BfuPreferences.DOCKER_STORAGE_OVERLAY2 to stringResource(R.string.dawnshell_docker_storage_overlay2),
                BfuPreferences.DOCKER_STORAGE_VFS to stringResource(R.string.dawnshell_docker_storage_vfs),
            ),
            s.dockerStorage, { s.dockerStorage = it }, Modifier.padding(top = 8.dp),
        )
        Small(
            stringResource(R.string.dawnshell_docker_storage_warning), Modifier.padding(top = 4.dp),
            color = colors.error,
        )
        Callout(colors.errorContainer, colors.onErrorContainer, Modifier.padding(top = 8.dp)) {
            Small(stringResource(R.string.dawnshell_docker_policy_warning), color = colors.onErrorContainer)
        }
        Small(s.dockerPolicyStatus, Modifier.padding(top = 14.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.dawnshell_view_compatibility_log),
            { a.openLog(DawnShellLogRepository.COMPATIBILITY) },
            Modifier.fillMaxWidth().padding(top = 8.dp), icon = logIcon,
        )
    }

    SectionCard {
        SectionTitle(stringResource(R.string.dawnshell_section_diagnostics))
        Body(stringResource(R.string.dawnshell_section_diagnostics_description), Modifier.padding(top = 4.dp))
        Small(s.probeSummary, Modifier.padding(top = 14.dp, start = 4.dp), maxLines = 4)
        DawnShellButton(
            stringResource(R.string.bfu_refresh_probe_status), a::refreshProbes,
            Modifier.fillMaxWidth().padding(top = 10.dp),
        )
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

    SectionCard(containerColor = colors.errorContainer) {
        SectionTitle(stringResource(R.string.dawnshell_section_danger), colors.onErrorContainer)
        Body(
            stringResource(R.string.dawnshell_section_danger_description), Modifier.padding(top = 4.dp),
            colors.onErrorContainer,
        )
        DawnShellButton(
            stringResource(R.string.bfu_remove_debian_rootfs), a::confirmRootfsRemoval,
            Modifier.fillMaxWidth().padding(top = 14.dp), contentColor = colors.onErrorContainer,
        )
    }
}

@Composable
private fun BootDialogHost(state: BootUiState) {
    val dialog = state.dialogs.firstOrNull() ?: return
    val dismiss = { state.dialogs.removeAt(0) }
    val cancel = stringResource(android.R.string.cancel)
    val ok = stringResource(android.R.string.ok)
    when (dialog) {
        is BootDialog.Notice -> AlertDialog(
            onDismissRequest = { dismiss() },
            title = dialog.title?.let { title -> { Text(title) } },
            text = { Text(dialog.message) },
            confirmButton = { TextButton(onClick = { dismiss() }) { Text(ok) } },
        )
        is BootDialog.Confirm -> AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            dismissButton = { TextButton(onClick = { dismiss() }) { Text(cancel) } },
            confirmButton = {
                TextButton(onClick = {
                    dismiss()
                    dialog.onConfirm()
                }) { Text(dialog.confirmLabel) }
            },
        )
        is BootDialog.Guide -> AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text(dialog.title) },
            text = {
                SelectionContainer {
                    Text(
                        dialog.body, Modifier.verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) { Text(stringResource(R.string.dawnshell_codec_ffmpeg_guide_close)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    dismiss()
                    dialog.onCopy()
                }) { Text(stringResource(R.string.dawnshell_codec_ffmpeg_guide_copy)) }
            },
        )
        is BootDialog.TypedConfirm -> {
            val input = remember(dialog) { TextFieldState() }
            AlertDialog(
                onDismissRequest = { dismiss() },
                title = { Text(dialog.title) },
                text = {
                    Column {
                        Text(dialog.message)
                        OutlinedTextField(
                            state = input,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            label = { Text(dialog.hint) },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                            ),
                        )
                    }
                },
                dismissButton = { TextButton(onClick = { dismiss() }) { Text(cancel) } },
                confirmButton = {
                    TextButton(onClick = {
                        val value = input.text.toString()
                        dismiss()
                        dialog.onConfirm(value)
                    }) { Text(dialog.confirmLabel) }
                },
            )
        }
    }
}
