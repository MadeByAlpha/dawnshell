@file:JvmName("BootScreenKt")
@file:JvmMultifileClass

package me.aroxu.dawnshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import me.aroxu.dawnshell.ui.DawnShellButton


@Composable
internal fun AdvancedPage(a: BootActions, navigate: (NavKey) -> Unit) {
    AccountsSection(navigate)
    AdvancedSection(navigate)
    DiagnosticsSection(navigate)

    SectionCard(
        Modifier.padding(top = 16.dp),
        containerColor = MaterialTheme.colorScheme.errorContainer
    ) {
        SectionTitle(stringResource(R.string.dawnshell_section_danger), MaterialTheme.colorScheme.onErrorContainer)
        Body(
            stringResource(R.string.dawnshell_section_danger_description), Modifier.padding(top = 4.dp),
            MaterialTheme.colorScheme.onErrorContainer,
        )
        DawnShellButton(
            stringResource(R.string.bfu_remove_debian_rootfs), a::confirmRootfsRemoval,
            Modifier.fillMaxWidth().padding(top = 14.dp), contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun AccountsSection(navigate: (NavKey) -> Unit) {
    SubTitle(stringResource(R.string.dawnshell_section_accounts), Modifier.padding(top = 16.dp))
    //Small(stringResource(R.string.dawnshell_section_accounts_description), Modifier.padding(vertical = 4.dp))

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, 1),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = { Text(stringResource(R.string.bfu_password_menu_title, "account")) },
            supportingContent = {},
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { navigate(DetailRoute(DetailPage.ACCOUNT)) }
        )
//        SegmentedListItem(
//            shapes = ListItemDefaults.segmentedShapes(0, 2),
//            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
//            content = { Text(stringResource(R.string.bfu_password_menu_title, "root")) },
//            supportingContent = {},
//            trailingContent = {
//                Icon(
//                    painterResource(R.drawable.ic_chevron_right_24),
//                    contentDescription = null,  // TODO: Add proper string resource
//                    tint = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
//        )
//        SegmentedListItem(
//            shapes = ListItemDefaults.segmentedShapes(1, 2),
//            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
//            content = { Text(stringResource(R.string.bfu_password_menu_title, "debian")) },
//            supportingContent = {},
//            trailingContent = {
//                Icon(
//                    painterResource(R.drawable.ic_chevron_right_24),
//                    contentDescription = null,  // TODO: Add proper string resource
//                    tint = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
//        )
    }
}

@Composable
private fun AdvancedSection(navigate: (NavKey) -> Unit) {
    SubTitle(stringResource(R.string.dawnshell_section_advanced), Modifier.padding(top = 16.dp))

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, 3),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = { Text(stringResource(R.string.dawnshell_host_usb_title)) },
            supportingContent = { Text(stringResource(R.string.dawnshell_host_usb_description)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { navigate(DetailRoute(DetailPage.USB)) }
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(1, 3),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = { Text(stringResource(R.string.dawnshell_codec_section_title)) },
            supportingContent = { Text(stringResource(R.string.dawnshell_codec_section_description)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { navigate(DetailRoute(DetailPage.CODEC)) }
        )
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(2, 3),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = { Text(stringResource(R.string.dawnshell_section_compatibility)) },
            supportingContent = { Text(stringResource(R.string.dawnshell_section_compatibility_description)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { navigate(DetailRoute(DetailPage.COMPATIBILITY)) }
        )
    }
}

@Composable
private fun DiagnosticsSection(navigate: (NavKey) -> Unit) {
    SubTitle(stringResource(R.string.dawnshell_section_diagnostics), Modifier.padding(top = 16.dp))

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(0, 1),
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            content = { Text(stringResource(R.string.dawnshell_section_diagnostics)) },
            supportingContent = { Text(stringResource(R.string.dawnshell_section_diagnostics_description)) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_chevron_right_24),
                    contentDescription = null,  // TODO: Add proper string resource
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { navigate(DetailRoute(DetailPage.DIAGNOSTICS)) }
        )
    }
}

@Composable
internal fun BootDialogHost(state: BootUiState) {
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
