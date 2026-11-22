#!/usr/bin/env bash
# Pins the safety invariants of the namespace/cgroup launcher source.
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
namespace_source="$repo_dir/app/src/main/cpp/bfu_namespace_probe.c"

if grep -Eq 'unshare[[:space:]]*\([[:space:]]*CLONE_NEWIPC' "$namespace_source"; then
    echo "Unsafe CLONE_NEWIPC call is forbidden on legacy 4.4-era kernels" >&2
    exit 6
fi
for marker in \
    'prepare_devices_cgroup_mount(control_dir, host_usb_policy)' \
    'init_moved_to_devices_cgroup' \
    'cgroup_view_devices_bind' \
    'cleanup_cgroup_subtree_failed' \
    'devices_cgroup=delegated' \
    'prepare_unified_cgroup_mount(control_dir, host_usb_policy)' \
    'cgroup_v2_device_bpf_verified' \
    'BPF_F_ALLOW_MULTI' \
    'command_moved_to_cgroup_v2_leaf' \
    'cgroup_requested=auto' \
    'fallback=v1' \
    'cgroup_delegation=delegated' \
    'CGROUP_POLICY_FORCE_V2' \
    'CGROUP_POLICY_FORCE_V1' \
    'HOST_USB_EXCLUSIVE' \
    'reconcile_exclusive_usb' \
    'restore_exclusive_usb' \
    'exclusive_mode_requires_at_least_one_VID:PID'; do
    grep -Fq "$marker" "$namespace_source" || {
        echo "Missing namespace/cgroup invariant: $marker" >&2
        exit 7
    }
done
if grep -Eq 'uname[[:space:]]*\([^)]*-r|/proc/version' "$namespace_source"; then
    echo "Cgroup selection must probe capabilities, not kernel version strings" >&2
    exit 8
fi

echo "PASS: namespace launcher invariants hold."
