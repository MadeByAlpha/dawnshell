@file:JvmName("BootScreenKt")
@file:JvmMultifileClass

package me.aroxu.dawnshell

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.Serializable
import me.aroxu.dawnshell.ui.DawnShellButton
import me.aroxu.dawnshell.ui.DawnShellScaffold


private enum class DashboardPage { SETUP, HOME, ADVANCED }

@Serializable
internal enum class DetailPage(@StringRes val title: Int, @StringRes val summary: Int) {
    ACCOUNT(R.string.dawnshell_section_accounts, R.string.dawnshell_section_accounts_description),
    USB(R.string.dawnshell_host_usb_title, R.string.dawnshell_host_usb_description),
    CODEC(R.string.dawnshell_codec_section_title, R.string.dawnshell_codec_section_description),
    COMPATIBILITY(R.string.dawnshell_section_compatibility, R.string.dawnshell_section_compatibility_description),
    DIAGNOSTICS(R.string.dawnshell_section_diagnostics, R.string.dawnshell_section_diagnostics_description),
}

@Serializable
private data object DashboardRoute : NavKey

@Serializable
internal data class DetailRoute(val page: DetailPage) : NavKey

@Composable
internal fun BootScreen(state: BootUiState, actions: BootActions, onDiscard: () -> Unit) {
    val backStack = rememberNavBackStack(DashboardRoute)

    LaunchedEffect(state) {
        snapshotFlow { state.usbDeviceIds.text.toString() }.drop(1).collect { state.usbDeviceIdsError = null }
    }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<DashboardRoute> {
                DashboardScreen(state, actions, onDiscard) { backStack.add(it) }
            }
            entry<DetailRoute> { route ->
                DetailScreen(stringResource(route.page.title), state, actions, { backStack.removeLastOrNull() }) {
                    when (route.page) {
                        DetailPage.ACCOUNT -> AccountPage(state, actions)
                        DetailPage.USB -> UsbPage(state, actions)
                        DetailPage.CODEC -> CodecPage(state, actions)
                        DetailPage.COMPATIBILITY -> CompatibilityPage(state, actions)
                        DetailPage.DIAGNOSTICS -> DiagnosticsPage(state, actions)
                    }
                }
            }
        },
    )
    BootDialogHost(state)
}

@Composable
private fun DashboardScreen(
    state: BootUiState,
    actions: BootActions,
    onDiscard: () -> Unit,
    navigate: (NavKey) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(DashboardPage.HOME) }
    val scroll = rememberScrollState()
    var menuOpen by remember { mutableStateOf(false) }
    val discardTitle = stringResource(R.string.dawnshell_discard_changes_title)
    val discardMessage = stringResource(R.string.dawnshell_discard_changes_message)
    val discardLabel = stringResource(R.string.dawnshell_discard_changes)

    LaunchedEffect(page) { scroll.scrollTo(0) }
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
                    NavigationItem(page == DashboardPage.SETUP, { page = DashboardPage.SETUP },
                        R.drawable.ic_logs_24, R.string.dawnshell_navigation_setup)
                    NavigationItem(page == DashboardPage.HOME, { page = DashboardPage.HOME },
                        R.drawable.ic_home_24, R.string.dawnshell_navigation_home)
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
                DashboardPage.SETUP -> SetupPage(state, actions)
                DashboardPage.HOME -> HomePage(state, actions)
                DashboardPage.ADVANCED -> AdvancedPage(actions, navigate)
            }
        }
    }
}

@Composable
private fun DetailScreen(
    title: String,
    s: BootUiState,
    a: BootActions,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DawnShellScaffold(
        title = title,
        onBack = onBack,
        bottomBar = { if (s.settingsDirty) ApplyBar(a) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            content = content,
        )
    }
}

// TODO: Replace with snackbar
@Composable
private fun ApplyBar(actions: BootActions) {
    Card(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
