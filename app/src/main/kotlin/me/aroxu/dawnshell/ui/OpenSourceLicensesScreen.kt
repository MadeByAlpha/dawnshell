package me.aroxu.dawnshell.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.aroxu.dawnshell.R

@Composable
fun OpenSourceLicensesScreen(text: String, onBack: () -> Unit, onCopy: () -> Unit) {
    // A single Text of the whole bundle would exceed Compose's maximum layout height, so lines are laid out lazily.
    val lines = remember(text) { text.lines() }
    DawnShellScaffold(
        title = stringResource(R.string.dawnshell_open_source_licenses),
        onBack = onBack,
        actions = {
            IconButton(onClick = onCopy) {
                Icon(painterResource(R.drawable.ic_copy_24), stringResource(R.string.dawnshell_copy_all_licenses))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.dawnshell_licenses_description),
                Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TerminalPanel(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).weight(1f).fillMaxWidth()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        items(lines) { line -> Text(line, Modifier.fillMaxWidth(), style = consoleTextStyle()) }
                    }
                }
            }
        }
    }
}
