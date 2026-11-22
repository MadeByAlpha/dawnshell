# Native output schema

[한국어](native-output-schema.ko.md) · [Documentation](README.md) · [Architecture](architecture.md)

DawnShell's native helpers report results to the app as **JSON Lines**: one JSON object per line, UTF-8. The
writer is the dependency-free `app/src/main/cpp/dawnshell_json.h`; the app decodes lines into typed Kotlin
classes (`NativeEvent.kt`) with kotlinx.serialization. Free-form log lines on stderr (for example
`[epoch] BFU_DEBIAN_STAGE …`) are not part of the schema; the app skips every line that is not a JSON object.

## Envelope

```json
{"v":1,"ts":1700000000,"event":"running", "...":"..."}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `v` | int | Schema version. Fields may be added within a version; removing or changing one raises it. |
| `ts` | int | Unix time in seconds. |
| `event` | string | Event name, lower snake case. |
| `truncated` | bool, optional | A field did not fit the line buffer and was dropped. |

Numbers and booleans are native JSON types. A line is always valid JSON and is written with one `write(2)`.
Readers must ignore unknown fields and keep unknown events.

## `bfu-namespace-probe` events

| Event | Channel | Fields |
| --- | --- | --- |
| `running`, `starting_or_unknown` | stdout | `state`, `mode`, `supervisor_pid`, `init_host_pid`, `supervisor_identity_valid`, `init_identity_valid`, `namespace_topology_valid`, `cgroup_mode`, `host_usb_mode`, `ns`{`pid`,`mnt`,`uts`,`ipc`,`cgroup`,`net`}, `updated_epoch` |
| `stopped` | stdout | `last_state`, `mode`, `cgroup_mode`, `host_usb_mode`, `wait_status`, `updated_epoch` |
| `started`, `fallback_started` | stdout | `supervisor_pid`, `init_host_pid` or `sshd_pid`, `mode`, `cgroup_mode`, `host_usb_mode`, `host_usb_filter_count` |
| `start_failed` | stdout | `stage`, optional `errno`, `exit`, `child_exit`, `child_signal` |
| `failed` | stderr | `stage`, optional `errno`, `error`; the process exit code is unchanged |
| `health` | stdout | `mode` (`systemd`/`compat`), `healthy`, `pid1`, `system_state`, `dbus_service`, `dbus_bus`, `ssh_service`, `boot_proof_service`, `boot_proof_marker`, `default_target`, `target_state`, `listen_22`, `compat_ready`, `cgroup_mode`, `cgroup_delegation`, `devices_cgroup` |
| `diagnostic` | stdout | `label`, `content` (failed units, sanitized) |
| `namespace_ok`, `compat_ok` | stdout | `arch`, `debian`, plus `pid`, `proc1`, `init`, `systemctl`, `cgroup` or `mode` |
| `namespace_failed`, `compat_failed` | stdout | `stage` |
| `already_running`, `already_stopped`, `orphaned_init`, `orphaned_init_stopped`, `orphaned_instance`, `force_stopped`, `shutdown_test_requested`, `shutdown_test_completed` | stdout | event-specific PIDs and modes; decoded as `OtherEvent` |

Inside the Debian chroot the health and probe snippets are shell scripts. They print the same objects; string
values are filtered to `[A-Za-z0-9_./:@+=,; -]` so quotes and control characters cannot break the JSON.

## Status files

Each `*.status` file is one JSON object, `{"v":1,"ts":…,"state":"SUCCEEDED","summary":"…","detail":{…}}`.
`state` is one of `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `REJECTED`, `UNAVAILABLE`, `UNKNOWN`. For lifecycle
operations `detail.events` holds the newest eight native events. A pre-JSON `<millis> STATE text` file is
still read once and replaced on the next write.

## Compatibility rules

- Add fields freely; renaming, removing, or retyping a field raises `v`.
- The app and the helpers ship in one APK, so no cross-version negotiation exists.
- Exit codes of the helpers do not change.
