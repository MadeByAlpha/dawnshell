@file:JvmName("BootScreenKt")
@file:JvmMultifileClass

package me.aroxu.dawnshell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.aroxu.dawnshell.ui.DawnShellCard


@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable ColumnScope.() -> Unit,
) {
    DawnShellCard(modifier.fillMaxWidth().padding(bottom = 16.dp), containerColor = containerColor) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
internal fun SectionTitle(text: String, color: Color = Color.Unspecified) =
    Text(text, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
internal fun SubTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.titleSmall)

@Composable
internal fun Body(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, color = color, style = MaterialTheme.typography.bodyMedium)

@Composable
internal fun Small(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = Int.MAX_VALUE,
) = Text(
    text, modifier, color = color, style = MaterialTheme.typography.bodySmall,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis,
)

@Composable
internal fun Divider(top: Int = 20, bottom: Int = 18) =
    HorizontalDivider(Modifier.padding(top = top.dp, bottom = bottom.dp))

@Composable
internal fun SelectableSmall(text: String, modifier: Modifier = Modifier) =
    SelectionContainer { Small(text, modifier) }
