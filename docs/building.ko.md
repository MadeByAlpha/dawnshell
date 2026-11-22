# DawnShell 빌드와 배포

[English](building.md) · [문서 홈](README.ko.md) · [라이선스 안내](../LICENSES/README.md)

이 문서는 저장소에서 세 Android ABI의 내장 런타임과 DawnShell APK를 빌드하고,
검사하고, Release 묶음을 만드는 방법을 설명합니다.

## 필요한 도구

- JDK 17
- Android SDK Platform 36과 Build Tools 34.0.0
- Android NDK `29.0.14206865`
- Git과 Bash
- Python 3(문서 링크와 언어 쌍 검사)
- C 컴파일러, GNU make, Autoconf, Automake, libtool, Bison, GNU gettext,
  GNU awk, patch, sed, tar, gzip, bzip2, xz, coreutils
- Windows에서는 위 도구를 제공하는 MSYS2와 Windows OpenSSH

내장 런타임은 `armeabi-v7a`, `arm64-v8a`, `x86_64`를 모두 빌드하므로 Android
NDK가 반드시 필요합니다.

## 저장소 받기

```sh
git clone https://github.com/aroxu/dawnshell.git
cd dawnshell
```

현재 저장소는 별도 Termux 저장소에 의존하지 않습니다. Debian 최초 설치에 필요한
BusyBox, `pkgdetails`, `gpgv`, debootstrap 자료와 namespace 런처는 고정된 소스와
패치에서 직접 빌드합니다.

## 환경 변수

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
```

SDK에 NDK `29.0.14206865`를 설치하세요(`sdkmanager "ndk;29.0.14206865"`). Gradle이
`ndkVersion`으로 정확히 그 버전을 선택하므로 `ANDROID_NDK_HOME`은 필요 없습니다.

## 전체 빌드

```sh
./gradlew clean policyChecks :app:lintDebug :app:testDebugUnitTest :app:stageDebugApk
```

이 명령은 다음 작업을 수행합니다.

1. `buildSrc/`의 Gradle 태스크가 고정 소스에서 세 ABI의 부트스트랩 런타임(BusyBox,
   `pkgdetails`, `gpgv`, namespace 런처, `dawnshell-fdguard`, 코덱 client·worker)을
   빌드하고 모든 ELF를 검증해 생성 asset으로 포함합니다. 바이너리는 커밋하지
   않습니다.
2. Docker, USB, 코덱, FFmpeg, 재부팅 격리 정책 검사를 실행합니다.
3. Android unit test와 lint를 실행합니다.
4. debug APK를 만들고 `dist/`에 복사합니다.

네이티브 소스가 바뀌지 않았다면 Gradle의 up-to-date 검사와 빌드 캐시로 결과를
재사용합니다. 기본 병렬 빌드는 `make -j"$(nproc)"`입니다. 메모리가 부족하거나 다른
작업과 병행한다면 작업 수를 제한합니다.

```sh
DAWNSHELL_BUILD_JOBS=4 ./gradlew assembleBfuRuntimeAssets
```

하나의 ABI만 빠르게 확인하려면 다음을 사용할 수 있습니다. Release 검증에는 사용하지
마세요.

```sh
./gradlew -PdawnshellBfuAbis=arm64-v8a :app:assembleDebug
```

기본 출력 파일 이름은 다음 형식입니다.

```text
dist/dawnshell-app_v<version>+debug.apk
```

## 자주 쓰는 개별 검사

`./gradlew policyChecks`가 `scripts/tests/host/*.sh`를 모두 실행하며 `./gradlew check`에도
포함됩니다. Gradle이 디렉터리로 스크립트를 찾으므로 이 디렉터리에 새 스크립트를 두면
빌드 파일을 고치지 않아도 됩니다. `DAWNSHELL_BFU_ASSETS_DIR`를 읽는(빌드된 바이너리를
검사하는) 스크립트는 네이티브 런타임 빌드 뒤에 자동으로 실행됩니다. 각 스크립트를
단독으로 실행할 수도 있으며, 먼저 `./gradlew assembleBfuRuntimeAssets`로 바이너리를
빌드하세요.

```sh
./gradlew policyChecks
./scripts/tests/host/test-release-compliance.sh
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

실기기와 `adb`가 필요한 스크립트는 `scripts/tests/device/`에 있으며 Gradle이 실행하지
않습니다.

실기기 없이 통과하는 검사는 빌드·패키징·정적 정책을 확인합니다. BFU broadcast,
Magisk/SELinux, 실제 vendor MediaCodec과 네트워크 복구는
[실기기 테스트](testing.ko.md)가 별도로 필요합니다.

## APK 설치

```sh
./scripts/install.sh
```

또는 출력 파일을 직접 지정합니다.

```sh
adb install -r dist/dawnshell-app_v<version>+debug.apk
```

기존 앱과 서명 키가 다르면 Android가 업데이트를 거부합니다. 앱 제거는 앱의
DE/CE와 SSH 개인 키를 삭제하지만 `/data/local/debian`은 자동으로 삭제하지
않습니다.

## Release 묶음 만들기

작업 트리가 깨끗하고 APK가 준비된 상태에서 실행합니다.

```sh
DAWNSHELL_RELEASE_VERSION=0.3.1 \
  ./scripts/package-release.sh path/to/signed.apk dist/release
```

출력에는 다음 파일이 포함됩니다.

- 서명된 APK
- 정확한 commit의 corresponding source archive
- 라이선스 묶음
- 빌드 정보
- Release notes
- `SHA256SUMS`

스크립트는 source archive에 필수 소스, 패치, 설정, 라이선스가 실제로 들어 있는지
확인하고 모든 SHA-256을 다시 검증합니다.

## 서명

저장소의 공개 debug 키는 개발과 시험 전용입니다. 정식 배포에는 개인 keystore를
사용하고 외부에 공개하거나 저장소에 commit하지 않습니다.

GitHub tag Release는 다음 secret을 요구합니다.

- `DAWNSHELL_RELEASE_KEYSTORE_BASE64`
- `DAWNSHELL_RELEASE_KEY_ALIAS`
- `DAWNSHELL_RELEASE_STORE_PASSWORD`
- `DAWNSHELL_RELEASE_KEY_PASSWORD`

```sh
git tag -s v0.3.1 -m "DawnShell 0.3.1"
git push origin v0.3.1
```

## GitHub Actions

`.github/workflows/build.yml`은 pull request, `main` push, 수동 실행과 버전 tag에서
동작합니다.

- pull request: 전체 빌드와 검사를 실행하고 artifact를 보관합니다.
- `main`: 성공한 빌드마다 공개 debug 키로 서명한 새 일반 Release를 만들고
  Latest로 표시합니다. 태그는
  `continuous-<버전>-<실행 번호>-<재시도 번호>-<커밋>` 형식이며,
  기존 Release나 첨부 파일을 덮어쓰지 않습니다.
- `vMAJOR.MINOR.PATCH` tag: 개인 키로 빌드하고 검증한 뒤 정식 Release를 만듭니다.

CI는 ShellCheck, Markdown 링크·제목 anchor·영문/국문 문서 쌍 검사, 세 ABI 소스 빌드,
정책 회귀 검사, Android lint/unit test,
`apksigner`, 대응 소스와 라이선스 검사를 모두 통과해야 합니다.

`main` 빌드 Release는 저장소의 공개 개발 키로 서명되므로 같은 키로 서명한 APK끼리만
Android에서 업데이트할 수 있습니다. 버전 tag Release는 설정된 비공개 제품용
키를 사용합니다.

## 라이선스 준수

DawnShell 자체 코드는 MIT입니다. APK에 포함된 도구와 라이브러리는 각 upstream
라이선스를 유지합니다. 바이너리를 배포할 때는 `scripts/package-release.sh`가 만든
대응 소스와 라이선스 묶음을 함께 제공하세요.

- [서드파티 고지](../bfu-runtime/THIRD_PARTY_NOTICES.ko.md)
- [APK 라이선스 묶음 구조](../LICENSES/README.md)
- [고정 소스 목록](../bfu-runtime/sources/SOURCES.lock)
