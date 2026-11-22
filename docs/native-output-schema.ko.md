# 네이티브 출력 스키마

[English](native-output-schema.md) · [문서](README.ko.md) · [아키텍처](architecture.ko.md)

DawnShell의 네이티브 헬퍼는 결과를 앱에 **JSON Lines**(한 줄에 JSON 객체 하나, UTF-8)로 전달합니다. 출력은 의존성이 없는
`app/src/main/cpp/dawnshell_json.h`가 만들고, 앱은 kotlinx.serialization으로 타입이 있는 Kotlin 클래스(`NativeEvent.kt`)에
디코드합니다. stderr의 자유 형식 로그(예: `[epoch] BFU_DEBIAN_STAGE …`)는 스키마에 포함되지 않으며, 앱은 JSON 객체가 아닌 줄을
모두 건너뜁니다.

## 공통 필드

```json
{"v":1,"ts":1700000000,"event":"running", "...":"..."}
```

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `v` | int | 스키마 버전. 같은 버전 안에서는 필드 추가만 허용하고, 삭제·변경 시 올립니다. |
| `ts` | int | Unix 시간(초). |
| `event` | string | 이벤트 이름(소문자 snake case). |
| `truncated` | bool, 선택 | 일부 필드가 줄 버퍼에 맞지 않아 생략되었습니다. |

숫자와 불리언은 JSON 네이티브 타입입니다. 한 줄은 항상 유효한 JSON이며 `write(2)` 한 번으로 출력됩니다. 읽는 쪽은 알 수 없는
필드를 무시하고 알 수 없는 이벤트를 보존해야 합니다.

## `bfu-namespace-probe` 이벤트

| 이벤트 | 채널 | 필드 |
| --- | --- | --- |
| `running`, `starting_or_unknown` | stdout | `state`, `mode`, `supervisor_pid`, `init_host_pid`, `supervisor_identity_valid`, `init_identity_valid`, `namespace_topology_valid`, `cgroup_mode`, `host_usb_mode`, `ns`{`pid`,`mnt`,`uts`,`ipc`,`cgroup`,`net`}, `updated_epoch` |
| `stopped` | stdout | `last_state`, `mode`, `cgroup_mode`, `host_usb_mode`, `wait_status`, `updated_epoch` |
| `started`, `fallback_started` | stdout | `supervisor_pid`, `init_host_pid` 또는 `sshd_pid`, `mode`, `cgroup_mode`, `host_usb_mode`, `host_usb_filter_count` |
| `start_failed` | stdout | `stage`, 선택 `errno`, `exit`, `child_exit`, `child_signal` |
| `failed` | stderr | `stage`, 선택 `errno`, `error`. 프로세스 종료 코드는 바뀌지 않습니다. |
| `health` | stdout | `mode`(`systemd`/`compat`), `healthy`, `pid1`, `system_state`, `dbus_service`, `dbus_bus`, `ssh_service`, `boot_proof_service`, `boot_proof_marker`, `default_target`, `target_state`, `listen_22`, `compat_ready`, `cgroup_mode`, `cgroup_delegation`, `devices_cgroup` |
| `diagnostic` | stdout | `label`, `content`(실패한 유닛, 정제됨) |
| `namespace_ok`, `compat_ok` | stdout | `arch`, `debian`, 그리고 `pid`, `proc1`, `init`, `systemctl`, `cgroup` 또는 `mode` |
| `namespace_failed`, `compat_failed` | stdout | `stage` |
| `already_running`, `already_stopped`, `orphaned_init`, `orphaned_init_stopped`, `orphaned_instance`, `force_stopped`, `shutdown_test_requested`, `shutdown_test_completed` | stdout | 이벤트별 PID와 모드. `OtherEvent`로 디코드됩니다. |

Debian chroot 안의 health·probe 스니펫은 셸 스크립트이며 같은 객체를 출력합니다. 문자열 값은 `[A-Za-z0-9_./:@+=,; -]`만 남겨
따옴표나 제어 문자가 JSON을 깨뜨리지 못하게 합니다.

## 상태 파일

각 `*.status` 파일은 JSON 객체 하나입니다: `{"v":1,"ts":…,"state":"SUCCEEDED","summary":"…","detail":{…}}`.
`state`는 `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `REJECTED`, `UNAVAILABLE`, `UNKNOWN` 중 하나입니다. 수명 주기 작업의
`detail.events`에는 최신 네이티브 이벤트 8개가 들어 있습니다. JSON 이전의 `<millis> STATE text` 파일도 한 번은 읽히며 다음
쓰기에서 교체됩니다.

## 호환 규칙

- 필드 추가는 자유롭지만, 이름 변경·삭제·타입 변경은 `v`를 올립니다.
- 앱과 헬퍼는 한 APK로 배포되므로 버전 협상은 없습니다.
- 헬퍼의 종료 코드는 바뀌지 않습니다.
