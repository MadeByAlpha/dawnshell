package me.aroxu.dawnshell.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import me.aroxu.dawnshell.R

/** Scroll position within which the log is considered to be at its tail, allowing only layout rounding. */
private const val TAIL_TOLERANCE_PX = 2

@Composable
fun LogDetailScreen(
    title: String,
    description: String,
    readLog: () -> String,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val text = rememberTextFieldState()
    val scroll = rememberScrollState()
    var following by remember { mutableStateOf(true) }
    var refreshRequest by remember { mutableIntStateOf(0) }

    // Live refresh is paused while the user holds a selection, so replacing the text never drops it.
    suspend fun refresh() {
        if (!text.selection.collapsed) return
        val value = withContext(Dispatchers.IO) { readLog() }
        if (text.selection.collapsed && text.text.toString() != value) text.setTextAndPlaceCursorAtEnd(value)
    }

    RepeatWhileResumed(1_000L) { refresh() }
    LaunchedEffect(refreshRequest) { if (refreshRequest > 0) refresh() }

    LaunchedEffect(scroll) {
        var previous = scroll.value
        snapshotFlow { scroll.value }.collect { value ->
            if (scroll.isScrollInProgress && value < previous) following = false
            previous = value
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.filter { !it }.collect {
            following = scroll.value >= scroll.maxValue - TAIL_TOLERANCE_PX
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.maxValue }.collect { maximum -> if (following) scroll.scrollTo(maximum) }
    }

    DawnShellScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { onCopy(text.text.toString()) }) {
                Icon(painterResource(R.drawable.ic_copy_24), stringResource(R.string.dawnshell_copy_all))
            }
            IconButton(onClick = { refreshRequest++ }) {
                Icon(painterResource(R.drawable.ic_refresh_24), stringResource(R.string.dawnshell_refresh))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    description,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.dawnshell_live),
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TerminalPanel(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp).weight(1f).fillMaxWidth()) {
                BasicTextField(
                    state = text,
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),
                    readOnly = true,
                    textStyle = consoleTextStyle(),
                    cursorBrush = SolidColor(colorResource(R.color.ds_terminal_foreground)),
                )
            }
        }
    }
}
