# 커널 요구사항

[English](kernel-requirements.md) · [문서 홈](README.ko.md)

DawnShell은 Android의 커널을 그대로 씁니다. 가상 머신이 아니기 때문에 모든 기능이 그
커널에 무엇이 컴파일돼 있는지에 달려 있습니다. 기본 Android 커널은 컨테이너가 아니라
Android를 위해 만들어져서, 일부 기능은 아예 빠져 있습니다.

이 문서는 DawnShell에 필요한 것, 기능별로 추가로 필요한 것, 그리고 커널이 지원하지
않을 때 무엇을 해야 하는지를 정리합니다.

## 먼저 커널을 확인하세요

Debian 안에서 실행하세요. 아무것도 바꾸지 않는 명령입니다.

```bash
uname -r
grep -w mqueue /proc/filesystems          # 비어 있으면 POSIX 메시지 큐 없음
grep -w overlay /proc/filesystems         # 비어 있으면 overlayfs 없음
ls /proc/self/ns                          # 사용 가능한 네임스페이스 종류
cat /proc/filesystems | grep cgroup       # cgroup 지원 여부
```

커널 설정 전체를 제공하는 기기도 있습니다.

```bash
zcat /proc/config.gz | grep -E 'NAMESPACES|OVERLAY_FS|POSIX_MQUEUE|CGROUP'
```

다만 `/proc/config.gz`는 조심해서 보세요. 패치된 커널에서는 내용이 오래된 경우가 있어
항목이 없다고 해서 기능이 없다는 뜻은 아닙니다. 위의 실행 시점 확인이 더 정확합니다.

DawnShell도 일부를 직접 보고합니다. Docker 정책을 적용하면 고급 페이지 상태 줄에
`bridge_support`와 `mqueue_filesystem`이 표시되고, Debian 시작 로그에는 실제로 선택된
격리 방식이 남습니다.

## DawnShell 자체에 필요한 것

없으면 Debian 환경이 아예 시작되지 않습니다.

| 기능 | 커널 옵션 | 없을 때 |
| --- | --- | --- |
| 네임스페이스 | `CONFIG_NAMESPACES` | 아무것도 시작되지 않음 |
| 마운트 네임스페이스 | `CONFIG_NAMESPACES` | 아무것도 시작되지 않음 |
| UTS 네임스페이스 | `CONFIG_UTS_NS` | 아무것도 시작되지 않음 |
| cgroup | `CONFIG_CGROUPS` | 아무것도 시작되지 않음 |
| seccomp 필터 | `CONFIG_SECCOMP`, `CONFIG_SECCOMP_FILTER` | 커널 결함 우회 장치를 설치할 수 없음 |

지원 범위의 Android 커널이라면 이미 전부 들어 있습니다. 커널을 직접 빌드할 때 실수로
빼지 않도록 적어둡니다.

## systemd 전체 환경에 필요한 것

| 기능 | 커널 옵션 | 없을 때 |
| --- | --- | --- |
| PID 네임스페이스 | `CONFIG_PID_NS` | systemd가 PID 1로 실행될 수 없음 |
| cgroup 네임스페이스 | `CONFIG_CGROUPS` | systemd가 위임된 트리를 소유할 수 없음 |

PID 네임스페이스가 없으면 선택형 host-PID 호환 모드로 동작할 수 있습니다. 이 모드는
systemd 없이 OpenSSH만 직접 띄우며 cgroup 격리와 Docker를 쓸 수 없습니다. 격리가
사라지기 때문에 기본값이 아니라 사용자가 직접 켜야 합니다.

## Docker에 필요한 것

| 기능 | 커널 옵션 | 없을 때 |
| --- | --- | --- |
| overlayfs | `CONFIG_OVERLAY_FS` | `vfs` 스토리지 드라이버를 대신 사용 |
| 장치 제어, cgroup v2 | `CONFIG_CGROUP_BPF`, `CONFIG_BPF_SYSCALL` | cgroup v1 devices 컨트롤러로 전환 |
| 장치 제어, cgroup v1 | `CONFIG_CGROUP_DEVICE` | 장치 격리 불가, USB 패스스루 거부 |
| 자원 컨트롤러 | `CONFIG_MEMCG`, `CONFIG_CGROUP_SCHED`, `CONFIG_CPUSETS`, `CONFIG_CGROUP_FREEZER`, `CONFIG_CGROUP_PIDS` | 컨테이너 자원 제한이 무시되거나 거부됨 |
| POSIX 메시지 큐 | `CONFIG_POSIX_MQUEUE` | 자체 IPC 네임스페이스를 쓰는 컨테이너가 시작 실패 |

DawnShell의 관리형 래퍼가 `--ipc=host`를 넣어서 메시지 큐 마운트 자체를 건너뜁니다.
그래서 `CONFIG_POSIX_MQUEUE`가 없는 커널에서도 컨테이너가 뜹니다.

## Docker bridge 네트워크에 필요한 것

기본 Android 커널에서 가장 자주 빠져 있는 부분입니다.

| 기능 | 커널 옵션 |
| --- | --- |
| bridge 장치 | `CONFIG_BRIDGE` |
| veth 쌍 | `CONFIG_VETH` |
| bridge netfilter | `CONFIG_BRIDGE_NETFILTER` |
| 연결 추적 | `CONFIG_NF_CONNTRACK`, `CONFIG_NETFILTER_XT_MATCH_CONNTRACK` |
| NAT | `CONFIG_NF_NAT`, `CONFIG_IP_NF_NAT` |
| 마스커레이딩 | `CONFIG_IP_NF_TARGET_MASQUERADE` |
| 주소 유형 매치 | `CONFIG_NETFILTER_XT_MATCH_ADDRTYPE` |
| nftables, 선택 | `CONFIG_NF_TABLES`, `CONFIG_NF_TABLES_IPV4` |

Docker의 bridge 드라이버는 아웃바운드 NAT에 마스커레이드 타깃을, 포트 공개에 주소 유형
매치를 씁니다. 둘 중 하나만 없어도 모든 bridge 정책이 실패합니다.

DawnShell은 정책을 적용할 때마다 이를 검사해 `bridge_support`로 보고합니다. 값이
`unavailable`이면 이 커널에서는 어떤 bridge 정책도 성공할 수 없으며, 호스트 전용이
기본값인 것은 DawnShell의 선택이 아니라 커널의 한계입니다.

bridge를 못 쓰면 컨테이너는 호스트 네트워크를 씁니다. Android의 네트워크 스택을 공유하고
서로는 `127.0.0.1`로 통신하며, `-p` 포트 매핑은 사용할 수 없습니다.

## 선택 기능

| 기능 | 커널 옵션 | 비고 |
| --- | --- | --- |
| USB 시리얼 장치 | `CONFIG_USB_SERIAL`과 해당 어댑터 드라이버 | `/dev/ttyUSB*`로 노출 |
| USB 저장장치 | `CONFIG_USB_STORAGE` | 같은 파일시스템을 Android와 Debian에서 동시에 마운트하지 마세요 |
| USB 이더넷 | 어댑터 드라이버, 예를 들어 `CONFIG_USB_RTL8152` | 네트워크 스택을 공유하므로 자동으로 나타남 |
| raw USB 패스스루 | 동작하는 장치 제어, 위 Docker 표 참고 | 장치 제어가 없으면 거부됨 |
| Tailscale 등 VPN | `CONFIG_TUN` | 커널 모드 네트워킹에 필요 |
| 하드웨어 영상 코덱 | 없음 | Android MediaCodec을 사용자 공간으로 사용 |

## DawnShell이 우회하는 알려진 커널 결함

옵션이 빠진 것이 아니라 출시된 커널의 결함입니다. DawnShell이 각각 탐지하거나 피해
가지만, 커널을 고르거나 빌드할 때 참고하시라고 적어둡니다.

| 결함 | 증상 | DawnShell의 대응 |
| --- | --- | --- |
| IPC 네임스페이스 생성 시 커널 패닉 | 컨테이너를 시작하면 Android가 재부팅 | 해당 호출을 차단하고 호스트 IPC를 공급 |
| 비정상적인 `close_range` 백포트 | systemd와 SSH가 시작 때 수 분간 멈춤 | 미구현으로 보고하는 가드 설치 |
| 컨테이너 생성 직후 overlay2 쓰기 실패 | `operation not permitted`로 간헐적 생성 실패 | `vfs` 스토리지 드라이버 선택지 제공 |
| Wi-Fi 드라이버가 zero-copy 전송을 버림 | 응답 헤더는 오는데 본문이 안 옴 | 해당 서비스에서 `sendfile` 끄기 |
| 네임스페이스 init이 `SIGKILL`에도 살아남음 | 정지한 인스턴스가 다음 시작을 막음 | 잔여물을 보고하고 재부팅을 안내 |

각 증상의 자세한 대처는 [문제 해결 가이드](troubleshooting.ko.md)에 있습니다.

## 커널이 지원하지 않는 기능이 필요하다면

위 항목이 빠져 있는데 그 기능이 꼭 필요하다면, **해당 옵션을 켜서 기기용 커널을 직접
빌드하는 것이 유일한 해법입니다.** 사용자 공간에서 없는 커널 기능을 만들어낼 방법은
없고, DawnShell도 있는 척하지 않습니다. 대신 실패 이유를 이름으로 알려줍니다.

현실적인 순서는 이렇습니다.

1. `/proc/config.gz`만 보지 말고, 이 문서 맨 위의 실행 시점 확인으로 무엇이 없는지
   확정합니다.
2. 기기와 Android 버전에 정확히 맞는 커널 소스를 구합니다. 커스텀 ROM 프로젝트가
   공개하는 경우가 많습니다.
3. 필요한 기능에 해당하는 옵션을 위 표에서 골라 켭니다. ROM이 이미 의존하는 옵션은
   그대로 두세요.
4. 빌드하고 플래시한 뒤 다시 확인합니다. Docker 정책 상태의 `bridge_support`와
   `mqueue_filesystem`으로 결과를 바로 알 수 있습니다.

시작하기 전에 그 기능이 정말 필요한지 한 번 더 생각해 보세요. 기기 한 대로 쓰는
대부분의 경우 호스트 네트워크가 bridge를 대신하고, 저장 공간을 더 쓰는 대신 `vfs`가
overlayfs를 대신합니다. 둘 다 임시 방편이 아니라 정식으로 지원하는 구성입니다.
