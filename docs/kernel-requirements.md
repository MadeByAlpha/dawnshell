# Kernel requirements

[한국어](kernel-requirements.ko.md) · [Documentation home](README.md)

DawnShell runs Debian on Android's own kernel. There is no virtual machine, so
every feature is limited by what that kernel was compiled with. Stock Android
kernels are built for Android, not for containers, so some features are simply
absent.

This page lists what DawnShell needs, what each optional feature needs, and what
to do when the kernel cannot provide it.

## Check your kernel first

Run these inside Debian. None of them change anything.

```bash
uname -r
grep -w mqueue /proc/filesystems          # empty means no POSIX message queues
grep -w overlay /proc/filesystems         # empty means no overlayfs
ls /proc/self/ns                          # available namespace types
cat /proc/filesystems | grep cgroup       # cgroup support
```

Some kernels also expose their full configuration:

```bash
zcat /proc/config.gz | grep -E 'NAMESPACES|OVERLAY_FS|POSIX_MQUEUE|CGROUP'
```

Treat `/proc/config.gz` with care. It can be stale on a patched kernel, and a
missing entry there does not always mean the feature is missing. The runtime
checks above are authoritative.

DawnShell reports several of these itself. After applying the Docker policy, the
status line on the Advanced page contains `bridge_support` and
`mqueue_filesystem`, and the Debian start log names the isolation mode it
settled on.

## Required for DawnShell

Without these the Debian environment cannot start at all.

| Capability | Kernel option | Without it |
| --- | --- | --- |
| Namespaces | `CONFIG_NAMESPACES` | Nothing starts |
| Mount namespace | `CONFIG_NAMESPACES` | Nothing starts |
| UTS namespace | `CONFIG_UTS_NS` | Nothing starts |
| cgroups | `CONFIG_CGROUPS` | Nothing starts |
| seccomp filter | `CONFIG_SECCOMP`, `CONFIG_SECCOMP_FILTER` | The kernel quirk guards cannot be installed |

Every Android kernel from the supported range already has these. They are listed
so a custom kernel build does not accidentally drop them.

## Required for the full systemd environment

| Capability | Kernel option | Without it |
| --- | --- | --- |
| PID namespace | `CONFIG_PID_NS` | systemd cannot run as PID 1 |
| cgroup namespace | `CONFIG_CGROUPS` | systemd cannot own a delegated tree |

When the PID namespace is unavailable, DawnShell can still run in the optional
host-PID compatibility mode. That mode starts OpenSSH directly, without systemd,
cgroup isolation, or Docker. It is opt-in because it removes isolation.

## Required for Docker

| Capability | Kernel option | Without it |
| --- | --- | --- |
| overlayfs | `CONFIG_OVERLAY_FS` | Use the `vfs` storage driver instead |
| Device gate, cgroup v2 | `CONFIG_CGROUP_BPF`, `CONFIG_BPF_SYSCALL` | Falls back to the cgroup v1 devices controller |
| Device gate, cgroup v1 | `CONFIG_CGROUP_DEVICE` | No device isolation; USB passthrough is refused |
| Resource controllers | `CONFIG_MEMCG`, `CONFIG_CGROUP_SCHED`, `CONFIG_CPUSETS`, `CONFIG_CGROUP_FREEZER`, `CONFIG_CGROUP_PIDS` | Container limits are ignored or rejected |
| POSIX message queues | `CONFIG_POSIX_MQUEUE` | A container with a private IPC namespace fails to start |

DawnShell's managed wrapper adds `--ipc=host`, which avoids the message queue
mount entirely. That is why containers still start on a kernel without
`CONFIG_POSIX_MQUEUE`.

## Required for Docker bridge networking

This is the feature most often missing on stock Android kernels.

| Capability | Kernel option |
| --- | --- |
| Bridge device | `CONFIG_BRIDGE` |
| veth pairs | `CONFIG_VETH` |
| Bridge netfilter | `CONFIG_BRIDGE_NETFILTER` |
| Connection tracking | `CONFIG_NF_CONNTRACK`, `CONFIG_NETFILTER_XT_MATCH_CONNTRACK` |
| NAT | `CONFIG_NF_NAT`, `CONFIG_IP_NF_NAT` |
| Masquerading | `CONFIG_IP_NF_TARGET_MASQUERADE` |
| Address type match | `CONFIG_NETFILTER_XT_MATCH_ADDRTYPE` |
| nftables, optional | `CONFIG_NF_TABLES`, `CONFIG_NF_TABLES_IPV4` |

Docker's bridge driver needs the masquerade target for outbound NAT and the
address type match for published ports. Missing either one makes every bridge
policy fail.

DawnShell probes this on every policy apply and reports the result as
`bridge_support`. A value of `unavailable` means no bridge policy can succeed
on this kernel, and the host-only default is a kernel limitation rather than a
DawnShell choice.

Without bridge networking, containers use host networking. They share Android's
network stack, reach each other over `127.0.0.1`, and cannot use `-p` port
mapping.

## Optional features

| Feature | Kernel option | Notes |
| --- | --- | --- |
| USB serial devices | `CONFIG_USB_SERIAL` and the adapter driver | Exposes `/dev/ttyUSB*` |
| USB storage | `CONFIG_USB_STORAGE` | Never mount one filesystem from Android and Debian at once |
| USB Ethernet | The adapter driver, such as `CONFIG_USB_RTL8152` | Appears automatically because the network stack is shared |
| Raw USB passthrough | A working device gate, see the Docker table | Refused when no device gate exists |
| Tailscale and other VPNs | `CONFIG_TUN` | Needed for kernel-mode networking |
| Hardware video codec | None | Uses Android's MediaCodec through userspace |

## Known kernel quirks DawnShell works around

These are defects in shipped kernels rather than missing options. DawnShell
detects or avoids each one, so they are listed for anyone choosing or building a
kernel.

| Quirk | Effect | DawnShell's response |
| --- | --- | --- |
| Creating an IPC namespace panics the device | Android reboots when a container starts | The call is blocked and host IPC is supplied |
| Pathological `close_range` backport | systemd and SSH stall for minutes at startup | A guard reports the call as unimplemented |
| overlay2 write failures right after a container is created | Container creation fails intermittently with `operation not permitted` | Offers the `vfs` storage driver |
| Wi-Fi driver drops zero-copy sends | Response headers arrive but the body never does | Disable `sendfile` in the affected service |
| Namespace init survives `SIGKILL` | A stopped instance blocks the next start | Reports the leftover and asks for a reboot |

The [troubleshooting guide](troubleshooting.md) covers each symptom in detail.

## When the kernel cannot do what you need

If a capability above is missing and you need the feature, the only real
solution is to build a kernel for your device with the option enabled. Nothing
in userspace can add a missing kernel feature, and DawnShell will not pretend
otherwise. It fails with a named reason instead.

A practical order of work:

1. Confirm the gap with the runtime checks at the top of this page rather than
   with `/proc/config.gz` alone.
2. Find the kernel source for your exact device and Android version. Custom ROM
   projects usually publish one.
3. Enable the options from the tables above that your feature needs, keeping
   every option the ROM already relies on.
4. Build, flash, and re-run the checks. `bridge_support` and
   `mqueue_filesystem` in the Docker policy status confirm the result.

Before starting, consider whether you need the feature at all. Host networking
replaces bridge networking for most single-device workloads, and the `vfs`
storage driver replaces overlayfs at the cost of disk space. Both are supported
configurations, not workarounds.
