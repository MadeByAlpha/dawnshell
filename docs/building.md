# DawnShell build and release

[한국어](building.ko.md) · [Documentation](README.md) · [License bundle](../LICENSES/README.md)

This guide builds all three embedded native ABIs, verifies the app, and creates
a complete release distribution.

## Requirements

- JDK 17
- Android SDK Platform 36 and Build Tools 34.0.0
- Android NDK `29.0.14206865`
- Git and Bash
- Python 3 for documentation validation
- C compiler, GNU make, Autoconf, Automake, libtool, Bison, GNU gettext,
  GNU awk, patch, sed, tar, gzip, bzip2, xz, and coreutils
- MSYS2 plus Windows OpenSSH when building on Windows

## Clone and configure

```sh
git clone https://github.com/aroxu/dawnshell.git
cd dawnshell

export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
```

Install NDK `29.0.14206865` in the SDK (`sdkmanager "ndk;29.0.14206865"`). Gradle
selects exactly that version through `ndkVersion`, so `ANDROID_NDK_HOME` is not
needed.

DawnShell does not depend on separate Termux repositories. BusyBox,
`pkgdetails`, `gpgv`, debootstrap material, and the namespace launcher are built
from pinned source and patches in this repository.

## Full build

```sh
./gradlew clean policyChecks :app:lintDebug :app:testDebugUnitTest :app:stageDebugApk
```

Gradle builds the `armeabi-v7a`, `arm64-v8a`, and `x86_64` bootstrap runtimes
(BusyBox, `pkgdetails`, `gpgv`, the namespace launcher, `dawnshell-fdguard`, and
the codec client and worker) from the pinned sources with the tasks in
`buildSrc/`, validates every ELF, and packages the result as generated assets.
The binaries are not committed. Gradle then runs the policy and bridge
regressions, Android lint and unit tests, and copies the debug APK to `dist/`.
Unchanged native sources are reused from Gradle's up-to-date checks and build
cache.

Compilation defaults to `make -j"$(nproc)"`. Limit parallelism when necessary:

```sh
DAWNSHELL_BUILD_JOBS=4 ./gradlew assembleBfuRuntimeAssets
```

For a local iteration on one ABI:

```sh
./gradlew -PdawnshellBfuAbis=arm64-v8a :app:assembleDebug
```

Do not use that shortcut as release evidence. The normal output is:

```text
dist/dawnshell-app_v<version>+debug.apk
```

## Individual checks

`./gradlew policyChecks` runs every `scripts/tests/host/*.sh` script (also part of
`./gradlew check`). Gradle discovers them by directory, so a new script there needs
no build change; scripts that read `DAWNSHELL_BFU_ASSETS_DIR` (they inspect the
built binaries) automatically run after the native runtime build. Each script can
also be run on its own; build the binaries first with
`./gradlew assembleBfuRuntimeAssets`.

```sh
./gradlew policyChecks
./scripts/tests/host/test-release-compliance.sh
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Scripts that need a phone and `adb` live in `scripts/tests/device/` and are never
run by Gradle.

These prove host-build and static policy properties. Direct Boot broadcasts,
Magisk/SELinux behavior, vendor MediaCodec, and network recovery still require
the [physical-device test plan](testing.md).

## Install a debug build

```sh
./scripts/install.sh
# or
adb install -r dist/dawnshell-app_v<version>+debug.apk
```

Android refuses an update signed by a different key. Uninstalling removes app
DE/CE data and its private SSH key, but does not remove `/data/local/debian`.

## Package a release

Use a clean worktree and a prepared signed APK:

```sh
DAWNSHELL_RELEASE_VERSION=0.3.1 \
  ./scripts/package-release.sh path/to/signed.apk dist/release
```

The output contains the signed APK, corresponding source for the exact commit,
a license archive, build metadata, release notes, and `SHA256SUMS`. The packager
verifies that all required source, patches, configuration, and notices are in
the source archive.

## Signing and GitHub Actions

The public debug key is for development only. Tagged production builds require:

- `DAWNSHELL_RELEASE_KEYSTORE_BASE64`
- `DAWNSHELL_RELEASE_KEY_ALIAS`
- `DAWNSHELL_RELEASE_STORE_PASSWORD`
- `DAWNSHELL_RELEASE_KEY_PASSWORD`

```sh
git tag -s v0.3.1 -m "DawnShell 0.3.1"
git push origin v0.3.1
```

`.github/workflows/build.yml` runs on pull requests, `main`, manual dispatch,
and version tags. It also checks shell scripts, Markdown links, heading anchors,
and English/Korean document pairs. Every successful `main` build creates a new,
debug-signed release with a unique `continuous-<version>-<run>-<attempt>-<commit>`
tag and
marks it as the latest release without overwriting earlier releases or assets; a
`vMAJOR.MINOR.PATCH` tag builds with private signing, verifies the complete
distribution, and publishes a stable release.

Each `main` build release is intentionally signed with the repository's public
development key. Android can update it only with another APK signed by
that same key. Version tags use the configured private production key instead.

## License compliance

DawnShell application code is MIT licensed. Bundled tools and libraries retain
their upstream licenses. Distribute the corresponding-source and license
archives produced by `scripts/package-release.sh` with every APK.

- [Third-party notices](../bfu-runtime/THIRD_PARTY_NOTICES.md)
- [License bundle layout](../LICENSES/README.md)
- [Pinned sources](../bfu-runtime/sources/SOURCES.lock)
