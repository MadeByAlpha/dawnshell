import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
}

val releaseStorePath = System.getenv("DAWNSHELL_RELEASE_STORE_FILE").orEmpty()
val releaseKeyAlias = System.getenv("DAWNSHELL_RELEASE_KEY_ALIAS").orEmpty()
val releaseStorePassword = System.getenv("DAWNSHELL_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyPassword = System.getenv("DAWNSHELL_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningValues = listOf(
    releaseStorePath, releaseKeyAlias, releaseStorePassword, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { it.isNotEmpty() }
if (releaseSigningValues.any { it.isNotEmpty() } && !releaseSigningConfigured) {
    throw GradleException("Release signing is partially configured. Set all DAWNSHELL_RELEASE_* variables.")
}

var sourceRevision = System.getenv("DAWNSHELL_SOURCE_REVISION").orEmpty()
if (sourceRevision.isEmpty()) {
    try {
        val process = ProcessBuilder("git", "rev-parse", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        if (process.waitFor() == 0 && Regex("[0-9a-fA-F]{40}").matches(output)) {
            sourceRevision = output.lowercase(Locale.ROOT)
        }
    } catch (ignored: Exception) {
        // A source offer without an exact revision is rejected before packaging.
    }
}

val generatedLicenseAssetsDir = layout.buildDirectory.dir("generated/openSourceLicenseAssets")

val appVersionName = System.getenv("DAWNSHELL_APP_BUILD__APP_VERSION_NAME").orEmpty()
val appVersionCode = System.getenv("DAWNSHELL_APP_BUILD__APP_VERSION_CODE").orEmpty()
val apkVersionTag = System.getenv("DAWNSHELL_APP_BUILD__APK_VERSION_TAG").orEmpty()

fun validateVersionName(versionName: String) {
    // https://semver.org/spec/v2.0.0.html#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string
    val semver = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)""" +
            """(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""")
    if (!semver.matches(versionName)) {
        throw GradleException("The versionName '$versionName' is not a valid version as per semantic version " +
            "'2.0.0' spec in the format 'major.minor.patch(-prerelease)(+buildmetadata)'. " +
            "https://semver.org/spec/v2.0.0.html.")
    }
}

android {
    namespace = "me.aroxu.dawnshell"

    compileSdk = providers.gradleProperty("compileSdkVersion").get().toInt()

    defaultConfig {
        applicationId = "me.aroxu.dawnshell"
        minSdk = providers.gradleProperty("minSdkVersion").get().toInt()
        targetSdk = providers.gradleProperty("targetSdkVersion").get().toInt()
        versionCode = 9
        versionName = "0.4.0"

        if (appVersionName.isNotEmpty()) versionName = appVersionName
        if (appVersionCode.isNotEmpty()) {
            if (!Regex("[1-9][0-9]*").matches(appVersionCode)) {
                throw GradleException("Invalid Android versionCode: $appVersionCode")
            }
            versionCode = appVersionCode.toInt()
        }
        validateVersionName(checkNotNull(versionName))
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStorePath)
                keyAlias = releaseKeyAlias
                storePassword = releaseStorePassword
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(generatedLicenseAssetsDir.get().asFile.path)
        val apkName = "dawnshell-app_" + apkVersionTag.ifEmpty { "v${android.defaultConfig.versionName}+${variant.buildType}" } + ".apk"
        variant.outputs.forEach { (it as VariantOutputImpl).outputFileName.set(apkName) }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("net.i2p.crypto:eddsa:0.3.0")

    testImplementation("junit:junit:4.13.2")
}

val resolvedVersionName = android.defaultConfig.versionName

tasks.register("versionName") {
    doLast {
        print(resolvedVersionName)
    }
}

val bfuBootstrapAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")
val bfuBootstrapExecutables = listOf(
    "dawnshell-toolbox", "pkgdetails", "gpgv", "bfu-namespace-probe",
    "dawnshell-fdguard",
    "dawnshell-codec", "dawnshell-codec-worker")
val bfuBootstrapAssets = (bfuBootstrapAbis.flatMap { abi ->
    bfuBootstrapExecutables.map { "bin/$abi/$it" } + "bin/$abi/runtime.properties"
} + listOf(
    "bootstrap/debootstrap_1.0.141.tgz",
    "bootstrap/debian-archive-keyring_2025.1_all.deb",
    "bootstrap/SOURCES.lock",
    "codec-test/avc-baseline-128x96-10fps.h264",
    "codec-test/avc-baseline-128x96-10fps.properties",
    "codec-test/avc-baseline-1280x720-30fps-30f.h264",
    "codec-test/avc-baseline-1280x720-30fps-30f.properties",
    "codec-test/avc-high-1920x1080-30fps-60f.h264",
    "codec-test/avc-high-1920x1080-30fps-60f.properties",
    "codec-test/avc-high-1280x720-30fps-30f-b2.mp4",
    "codec-test/avc-high-1280x720-30fps-30f-b2.properties",
    "codec-test/hevc-main-1920x1080-30fps-60f.mp4",
    "codec-test/hevc-main-1920x1080-30fps-60f.properties",
    "dawnshell-codec-ffmpeg.pl",
    "dawnshell-codec-long-run.sh",
    "dawnshell-codec-concurrency-test.sh",
    "dawnshell-codec-error-test.sh",
)).map { file("src/main/assets/bfu/$it") }

val requiredLicenseFiles = listOf(
    "README.md",
    "ANDROID_DEPENDENCIES.md",
    "DawnShell-MIT.txt",
    "Apache-2.0.txt",
    "CC0-1.0.txt",
    "Expat.txt",
    "GPL-2.0-only.txt",
    "GPL-2.0-or-later.txt",
    "GPL-3.0-or-later.txt",
    "LGPL-2.1-or-later.txt",
    "LGPL-3.0-or-later.txt",
    "base-installer-1.226-copyright.txt",
    "debian-archive-keyring-2025.1-copyright.txt",
    "GnuPG-2.4.9-additional-notices.txt",
    "Libgcrypt-1.12.1-additional-notices.txt")

val syncOpenSourceLicenses = tasks.register<Sync>("syncOpenSourceLicenses") {
    from(file("$rootDir/LICENSES"))
    from(file("$rootDir/bfu-runtime/THIRD_PARTY_NOTICES.md"))
    into(generatedLicenseAssetsDir.map { it.dir("open_source_licenses") })
    inputs.property("sourceRevision", sourceRevision)
    doFirst {
        if (!Regex("[0-9a-f]{40}").matches(sourceRevision)) {
            throw GradleException("Cannot determine the exact Git source revision for the APK source offer.")
        }
    }
    doLast {
        val offer = generatedLicenseAssetsDir.get()
            .file("open_source_licenses/SOURCE_OFFER.txt").asFile
        offer.writeText("""DawnShell corresponding source

This APK was built from Git commit:
$sourceRevision

Exact source snapshot:
https://github.com/aroxu/dawnshell/archive/$sourceRevision.tar.gz

Repository and build instructions:
https://github.com/aroxu/dawnshell/tree/$sourceRevision

Official releases attach a dawnshell-*-corresponding-source.tar.gz archive
beside the APK. That archive includes the pinned upstream source archives,
Android patches, configuration, and scripts used to control compilation and
installation. No password or fee is required to obtain it.
""", Charsets.UTF_8)
    }
}

val verifyOpenSourceLicenses = tasks.register("verifyOpenSourceLicenses") {
    inputs.files(requiredLicenseFiles.map { file("$rootDir/LICENSES/$it") })
    inputs.file(file("$rootDir/bfu-runtime/THIRD_PARTY_NOTICES.md"))
    doLast {
        requiredLicenseFiles.forEach { name ->
            val license = file("$rootDir/LICENSES/$name")
            if (!license.isFile || license.length() == 0L) {
                throw GradleException("Missing open-source license document: $license")
            }
        }
        val sourceLock = file("$rootDir/bfu-runtime/sources/SOURCES.lock").readText()
        listOf(
            "GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-or-later",
            "LGPL-2.1-or-later", "LGPL-3.0-or-later", "Expat"
        ).forEach { identifier ->
            if (!sourceLock.contains(identifier)) {
                throw GradleException("SOURCES.lock is missing license identifier: $identifier")
            }
        }
    }
}

val verifyBfuBootstrapRuntime = tasks.register("verifyBfuBootstrapRuntime") {
    inputs.files(bfuBootstrapAssets)
    doLast {
        bfuBootstrapAssets.forEach { asset ->
            if (!asset.isFile) {
                throw GradleException("Missing BFU bootstrap asset: $asset")
            }
            if (asset.length() == 0L) {
                throw GradleException("Empty BFU bootstrap asset: $asset")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(verifyBfuBootstrapRuntime)
    dependsOn(verifyOpenSourceLicenses)
    dependsOn(syncOpenSourceLicenses)
}
