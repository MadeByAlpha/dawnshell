#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
kotlin_dir="$repo_dir/app/src/main/kotlin/me/aroxu/dawnshell"
boot_screen="$kotlin_dir/BootActivity.BootScreen.kt"
actions="$kotlin_dir/BootScreen.kt"
advanced_page="$kotlin_dir/BootActivity.BootScreen.ConfigurationPage.kt"
activity="$kotlin_dir/BootActivity.kt"
service="$repo_dir/app/src/main/java/me/aroxu/dawnshell/BfuBootService.java"
guide="$repo_dir/docs/user-guide.md"

grep -Fq 'private enum class DashboardPage { SETUP, HOME, ADVANCED }' "$boot_screen"
[[ "$(grep -Fc 'NavigationItem(page == DashboardPage.' "$boot_screen")" -eq 3 ]]
advanced_line="$(grep -nF 'AdvancedSection(navigate)' "$advanced_page" | cut -d: -f1)"
diagnostics_line="$(grep -nF 'DiagnosticsSection(navigate)' "$advanced_page" | cut -d: -f1)"
(( diagnostics_line > advanced_line ))

# Pending settings are surfaced by one apply bar on the dashboard and on every detail page.
grep -Fq 'if (state.settingsDirty) ApplyBar(actions)' "$boot_screen"
grep -Fq 'if (s.settingsDirty) ApplyBar(a)' "$boot_screen"
[[ "$(grep -Fc 'actions::confirmApplySettings' "$boot_screen")" -eq 1 ]]

if grep -Eq 'fun (confirm)?Apply(HostUsb|Docker|Cgroup)' "$actions"; then
    echo "settings must not expose feature-specific apply actions" >&2
    exit 1
fi

grep -Fq 'SettingsSnapshot.fromPreferences(this)' "$activity"
grep -Fq 'requested.requiresRuntimeApply(previous)' "$activity"
grep -Fq 'BfuBootService.requestRuntimeSettingsApply(' "$activity"
[[ "$(grep -Fc 'savePreferences(requested)' "$activity")" -eq 1 ]]
if grep -Fq 'savePreferences()' "$activity"; then
    echo "immediate command buttons must not silently apply pending settings" >&2
    exit 1
fi
grep -Fq 'ACTION_APPLY_RUNTIME_SETTINGS' "$service"
grep -Fq 'HostUsbProvisioner.apply(this, layout)' "$service"
grep -Fq 'DockerNetworkProvisioner.apply(' "$service"
[[ "$(grep -Fc '"AFU_settings_apply"' "$service")" -eq 1 ]]
[[ "$(grep -Fc '"AFU_settings_applied"' "$service")" -eq 1 ]]

grep -Fq '| **Home** |' "$guide"
grep -Fq 'global **Apply** bar' "$guide"

echo "PASS: dashboard navigation and the single settings apply workflow are pinned."
