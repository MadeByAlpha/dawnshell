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
import androidx.compose.material3.IconButtonDefaults
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
            Box {
                var expanded by remember { mutableStateOf(false) }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painterResource(R.drawable.ic_more_vert_24),
                        stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                    )
                }
                DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShapes()
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bfu_status_debian_short)) },
                            onClick = {
                                expanded = false
                                a.requestLifecycle(DebianLauncher.Operation.STATUS)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dawnshell_view_runtime_log)) },
                            onClick = {
                                expanded = false
                                a.openLog(DawnShellLogRepository.LIFECYCLE)
                            },
                        )
                        HorizontalDivider(Modifier.padding(MenuDefaults.HorizontalDividerPadding))
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bfu_force_stop_debian_short)) },
                            onClick = {
                                expanded = false
                                a.confirmForceStop()
                            },
                        )
                    }
                }
            }
        }

        val status = s.lifecycleStatus as? StatusLoad.Loaded
        TerminalPanel(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(
                s.lifecycleStatus.text(
                    R.string.bfu_lifecycle_status_none,
                    R.string.bfu_lifecycle_status,
                    R.string.bfu_lifecycle_status_failed,
                    420,
                ),
                Modifier.padding(14.dp), style = consoleTextStyle(), maxLines = 5)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status != null)
                DawnShellButton(
                    stringResource(R.string.bfu_stop_debian_short),
                    a::confirmStop, Modifier.weight(1f), filled = true,
                )
            else
                DawnShellButton(
                    stringResource(R.string.bfu_start_debian_short),
                    { a.requestLifecycle(DebianLauncher.Operation.START) }, Modifier.weight(1f), filled = true,
                )
            IconButton(a::confirmRestart, shape = IconButtonDefaults.outlinedShape) {
                Icon(
                    painterResource(R.drawable.ic_refresh_24),
                    contentDescription = stringResource(R.string.bfu_restart_debian_short),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    Text(a.buildSshConnectCommand(), Modifier.padding(14.dp), style = consoleTextStyle())
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
