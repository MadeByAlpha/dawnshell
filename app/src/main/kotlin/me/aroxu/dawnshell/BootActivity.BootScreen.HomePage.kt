@file:JvmName("BootScreenKt")
@file:JvmMultifileClass

package me.aroxu.dawnshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.aroxu.dawnshell.ui.DawnShellButton
import me.aroxu.dawnshell.ui.TerminalPanel
import me.aroxu.dawnshell.ui.consoleTextStyle


@Composable
internal fun HomePage(s: BootUiState, a: BootActions) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.dawnshell_section_runtime))
                Small(stringResource(R.string.dawnshell_section_runtime_description), Modifier.padding(top = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    a.openLog(DawnShellLogRepository.LIFECYCLE)
                }) {
                    Icon(
                        painterResource(R.drawable.ic_logs_24),
                        stringResource(R.string.dawnshell_view_runtime_log)
                    )
                }
                IconButton(onClick = {
                    a.requestLifecycle(DebianLauncher.Operation.STATUS)
                }) {
                    Icon(
                        painterResource(R.drawable.ic_refresh_24),
                        stringResource(R.string.bfu_status_debian_short)    // TODO: Add proper string resource
                    )
                }
            }
        }

        // TODO: Rewrite this
        TerminalPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(s.lifecycleStatus, Modifier.padding(14.dp), style = consoleTextStyle(), maxLines = 5)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DawnShellButton(
                stringResource(R.string.bfu_start_debian_short),
                { a.requestLifecycle(DebianLauncher.Operation.START) }, Modifier.weight(1f), filled = true,
            )
            DawnShellButton(stringResource(R.string.bfu_restart_debian_short), a::confirmRestart, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DawnShellButton(
                stringResource(R.string.bfu_force_stop_debian_short), a::confirmForceStop,
                Modifier.weight(1f), contentColor = MaterialTheme.colorScheme.error,
            )
            DawnShellButton(stringResource(R.string.bfu_stop_debian_short), a::confirmStop, Modifier.weight(1f))
        }
    }

    SshAccessCard(s, a)
}

@Composable
private fun SshAccessCard(s: BootUiState, a: BootActions) {
    var menuOpen by remember { mutableStateOf(false) }

    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.dawnshell_section_access))
                Small(stringResource(R.string.dawnshell_section_access_description), Modifier.padding(top = 4.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = !menuOpen }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert_24),
                        stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    )
                }
                DropdownMenuPopup(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShapes()
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bfu_export_private_key_file)) },
                            onClick = {
                                menuOpen = false
                                a.confirmExportPrivateKey()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bfu_copy_key_import_command)) },
                            onClick = {
                                menuOpen = false
                                a.confirmCopyKeyImportCommand()
                            },
                        )
                        HorizontalDivider(Modifier.padding(MenuDefaults.HorizontalDividerPadding))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bfu_rotate_ssh_client_key)) },
                            onClick = {
                                menuOpen = false
                                a.confirmRotateSshKey()
                            },
                        )
                    }
                }
            }
        }
        TerminalPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
                SelectionContainer(Modifier.weight(1f)) {
                    // TODO: Change to ssh connection command
                    Text(s.generatedPublicKey, Modifier.padding(14.dp), style = consoleTextStyle())
                }
                IconButton(
                    onClick = { a.copySshConnectCommand() },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_copy_24),
                        contentDescription = stringResource(R.string.bfu_copy_ssh_connect_command)
                    )
                }
            }
        }
    }
}
