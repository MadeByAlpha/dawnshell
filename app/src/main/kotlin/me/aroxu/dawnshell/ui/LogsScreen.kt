package me.aroxu.dawnshell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.aroxu.dawnshell.R

class LogEntry(val type: String, @StringRes val title: Int, @StringRes val description: Int)

@Composable
fun LogsScreen(entries: List<LogEntry>, readSummary: (String) -> String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    var summaries by remember { mutableStateOf(emptyMap<String, String>()) }
    RepeatWhileResumed(1_000L) {
        summaries = withContext(Dispatchers.IO) { entries.associate { it.type to readSummary(it.type) } }
    }
    DawnShellScaffold(title = stringResource(R.string.dawnshell_logs), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.dawnshell_logs_explanation),
                Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            entries.forEach { entry ->
                LogCard(entry, summaries[entry.type].orEmpty(), onClick = { onOpen(entry.type) })
            }
        }
    }
}

@Composable
private fun LogCard(entry: LogEntry, summary: String, onClick: () -> Unit) {
    DawnShellCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(entry.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(entry.description),
                    Modifier.padding(top = 3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    summary,
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right_24),
                contentDescription = null,
                modifier = Modifier.padding(start = 12.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
