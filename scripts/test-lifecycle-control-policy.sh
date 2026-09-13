#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
service="$repo_dir/app/src/main/java/me/aroxu/dawnshell/BfuBootService.java"
receiver="$repo_dir/app/src/main/java/me/aroxu/dawnshell/BootReceiver.java"
launcher="$repo_dir/app/src/main/cpp/bfu_namespace_probe.c"
activity="$repo_dir/app/src/main/java/me/aroxu/dawnshell/BootActivity.java"
layout="$repo_dir/app/src/main/res/layout/activity_boot.xml"

grep -Fq 'lifecycleExecutor = Executors.newSingleThreadExecutor()' "$service"
grep -Fq 'lifecycleFuture.cancel(true)' "$service"
grep -Fq 'urgentControlGeneration.incrementAndGet()' "$service"
grep -Fq 'DEBIAN_LIFECYCLE_PREEMPTED_BY' "$service"
grep -Fq 'DEBIAN_AUTOSTART_SUPPRESSED' "$service"
grep -Fq 'requestLifecycleOperation(DebianLauncher.Operation.START,' "$service"
grep -Fq '"boot_completed_unlocked"' "$service"
grep -Fq 'ACTION_DEBIAN_FORCE_STOP' "$service"
grep -Fq 'DebianLauncher.Operation.FORCE_STOP' "$activity"
grep -Fq 'android:id="@+id/force_stop_debian_button"' "$layout"
grep -Fq 'strcmp(argv[1], "force-stop")' "$launcher"
grep -Fq 'refusing_to_kill_unverified_supervisor' "$launcher"
grep -Fq 'verified_supervisor_is_not_session_leader' "$launcher"
grep -Fq 'kill(-supervisor_pid, SIGKILL)' "$launcher"
# A force stop clears the recorded identity, so a leftover instance has to be
# discoverable from the delegated cgroup instead of from that record.
grep -Fq 'collect_delegated_processes' "$launcher"
grep -Fq 'terminate_delegated_processes' "$launcher"
grep -Fq 'orphaned_instance' "$launcher"
grep -Fq 'delegated_processes_survived_SIGKILL_reboot_required' "$launcher"
# The init of a PID namespace must be signalled after its children so they are
# never left without a reaper.
grep -Fq 'if (index != lowest) (void) kill(pids[index], SIGKILL);' "$launcher"
grep -Fq 'verified_processes_killed_but_kernel_lock_not_released' "$launcher"
if grep -A110 -F 'static int run_force_stop' "$launcher" | grep -Fq 'unlink(lock_path)'; then
    echo "Force stop must never unlink an actively held lock" >&2
    exit 1
fi
grep -A12 -F 'if (Intent.ACTION_BOOT_COMPLETED.equals(action))' "$receiver" \
    | grep -Fq 'startBfuEnvironment(context)'
if grep -A12 -F 'if (Intent.ACTION_BOOT_COMPLETED.equals(action))' "$receiver" \
        | grep -Fq '&& !unlocked'; then
    echo "BOOT_COMPLETED must start enabled Debian even when Android is unlocked" >&2
    exit 1
fi

if grep -Fq 'executor.execute(() -> {' "$service" \
        && grep -A8 -F 'private void requestLifecycleOperation' "$service" \
            | grep -Fq 'executor.execute'; then
    echo "Lifecycle controls must not use the background work queue" >&2
    exit 1
fi

echo "PASS: urgent lifecycle controls preempt health work and bypass the background queue."
