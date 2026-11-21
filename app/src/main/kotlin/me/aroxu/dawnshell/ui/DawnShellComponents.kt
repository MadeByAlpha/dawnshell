package me.aroxu.dawnshell.ui

import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import me.aroxu.dawnshell.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawnShellScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back_24), contentDescription = null)
                        }
                    }
                },
                actions = actions,
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun DawnShellCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick == null) {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    } else {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = content)
    }
}

@Composable
fun TerminalPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colorResource(R.color.ds_terminal_background)),
    ) { content() }
}

@Composable
fun consoleTextStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    color = colorResource(R.color.ds_terminal_foreground),
)

/** Runs [action] immediately and then every [intervalMillis] while the host lifecycle is RESUMED. */
@Composable
fun RepeatWhileResumed(intervalMillis: Long, action: suspend () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                currentAction()
                delay(intervalMillis)
            }
        }
    }
}

/** Filled or outlined button with the DawnShell shape and 48dp minimum height. */
@Composable
fun DawnShellButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    filled: Boolean = false,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
    icon: Painter? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    val sized = modifier.heightIn(min = 48.dp)
    val label: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        }
        Text(text)
    }
    if (filled) {
        Button(onClick, sized, enabled, shape, content = { label() })
    } else if (contentColor.isSpecified) {
        OutlinedButton(
            onClick, sized, enabled, shape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = BorderStroke(1.dp, contentColor),
            content = { label() },
        )
    } else {
        OutlinedButton(onClick, sized, enabled, shape, content = { label() })
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f).padding(end = 16.dp), color = contentColor, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun <T> RadioGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
) {
    Column(modifier) {
        options.forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
                Text(label, color = contentColor, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Rounded container used for explanations and warnings inside a card. */
@Composable
fun Callout(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = containerColor, contentColor = contentColor) {
        Column(Modifier.padding(14.dp), content = content)
    }
}
