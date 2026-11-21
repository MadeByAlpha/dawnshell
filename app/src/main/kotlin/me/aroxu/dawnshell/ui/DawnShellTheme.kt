package me.aroxu.dawnshell.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import me.aroxu.dawnshell.R

/** Maps the `ds_*` color resources, which already switch with the night qualifier, onto Material 3 roles. */
@Composable
fun DawnShellTheme(content: @Composable () -> Unit) {
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    val colorScheme = base.copy(
        primary = colorResource(R.color.ds_primary),
        onPrimary = colorResource(R.color.ds_on_primary),
        primaryContainer = colorResource(R.color.ds_primary_container),
        onPrimaryContainer = colorResource(R.color.ds_on_primary_container),
        secondary = colorResource(R.color.ds_secondary),
        onSecondary = colorResource(R.color.ds_on_secondary),
        secondaryContainer = colorResource(R.color.ds_secondary_container),
        onSecondaryContainer = colorResource(R.color.ds_on_secondary_container),
        tertiary = colorResource(R.color.ds_tertiary),
        onTertiary = colorResource(R.color.ds_on_tertiary),
        tertiaryContainer = colorResource(R.color.ds_tertiary_container),
        onTertiaryContainer = colorResource(R.color.ds_on_tertiary_container),
        background = colorResource(R.color.ds_background),
        onBackground = colorResource(R.color.ds_on_background),
        surface = colorResource(R.color.ds_surface),
        onSurface = colorResource(R.color.ds_on_surface),
        surfaceVariant = colorResource(R.color.ds_surface_variant),
        onSurfaceVariant = colorResource(R.color.ds_on_surface_variant),
        outline = colorResource(R.color.ds_outline),
        outlineVariant = colorResource(R.color.ds_outline_variant),
        error = colorResource(R.color.ds_error),
        onError = colorResource(R.color.ds_on_error),
        errorContainer = colorResource(R.color.ds_error_container),
        onErrorContainer = colorResource(R.color.ds_on_error_container),
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
