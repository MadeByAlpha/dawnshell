package me.aroxu.dawnshell.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

private const val DEFAULT_SOURCE_DATE_EPOCH = "1758672000"

private val COMPILE_FLAGS = listOf("-std=c17", "-Os", "-fPIE", "-fstack-protector-strong", "-Wall", "-Wextra", "-Werror", "-Wformat=2")
private val LINK_FLAGS = listOf("-Wl,-pie", "-Wl,-z,relro,-z,now", "-Wl,--gc-sections")

/**
 * Registers the source builds of every bundled native program for each ABI and returns the task that
 * assembles them into generated assets.
 */
fun Project.registerBfuRuntime(
    ndkDirectory: Provider<Directory>,
    ndkVersion: String,
    androidApi: Provider<Int>,
    abiIds: List<String>,
): TaskProvider<BfuRuntimeAssetsTask> {
    val runtimeDir = rootProject.layout.projectDirectory.dir("bfu-runtime")
    val sourcesDir = runtimeDir.dir("sources")
    val cppDir = layout.projectDirectory.dir("src/main/cpp")
    val binariesDir = layout.buildDirectory.dir("bfuRuntime/bin")
    val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH").orElse(DEFAULT_SOURCE_DATE_EPOCH)
    val jobs = providers.environmentVariable("DAWNSHELL_BUILD_JOBS")
        .map { value ->
            value.toIntOrNull()?.takeIf { it > 0 }
                ?: throw GradleException("DAWNSHELL_BUILD_JOBS must be a positive integer: $value")
        }
        .orElse(Runtime.getRuntime().availableProcessors())

    val extractPkgdetails = tasks.register("extractPkgdetailsSource", ExtractArchiveMemberTask::class.java) {
        archive.set(sourcesDir.file("base-installer_1.226.tar.xz"))
        sourceLock.set(sourcesDir.file("SOURCES.lock"))
        member.set("base-installer/pkgdetails.c")
        outputFile.set(layout.buildDirectory.file("bfuRuntime/pkgdetails.c"))
    }

    val abis = abiIds.map { Abi.of(it) }
    val allTasks = abis.flatMap { abi ->
        fun BfuNativeTask.configureCommon(program: String) {
            group = "bfu runtime"
            description = "Builds $program for ${abi.id}."
            this.ndkDirectory.set(ndkDirectory)
            this.ndkVersion.set(ndkVersion)
            abiId.set(abi.id)
            this.androidApi.set(androidApi)
            this.sourceDateEpoch.set(sourceDateEpoch)
        }

        fun compile(
            program: String,
            sources: List<Any>,
            compilerArguments: List<String>,
            linkerArguments: List<String>,
            kind: ElfKind,
        ) = tasks.register("buildBfu${abi.taskSuffix}${program.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }}", BfuCompileTask::class.java) {
            configureCommon(program)
            this.sources.from(sources)
            headers.from(fileTree(cppDir))
            this.compilerArguments.set(compilerArguments)
            this.linkerArguments.set(linkerArguments)
            elfKind.set(kind)
            outputFile.set(binariesDir.map { it.file("${abi.id}/$program") })
        }

        fun cpp(name: String) = cppDir.file(name)
        val hardened = COMPILE_FLAGS + LINK_FLAGS

        listOf(
            tasks.register("buildBfu${abi.taskSuffix}Busybox", BusyBoxTask::class.java) {
                configureCommon("dawnshell-toolbox")
                sourceArchive.set(sourcesDir.file("busybox-1.38.0.tar.bz2"))
                sourceLock.set(sourcesDir.file("SOURCES.lock"))
                patchFile.set(runtimeDir.file("patches/busybox/0001-android-mount-without-addmntent.patch"))
                configFragment.set(runtimeDir.file("config/busybox-bootstrap.config"))
                this.jobs.set(jobs)
                outputFile.set(binariesDir.map { it.file("${abi.id}/dawnshell-toolbox") })
            },
            tasks.register("buildBfu${abi.taskSuffix}Gpgv", GpgvTask::class.java) {
                configureCommon("gpgv")
                sourceArchives.from(
                    listOf(
                        "libgpg-error-1.59.tar.bz2", "libgcrypt-1.12.1.tar.bz2", "libassuan-3.0.2.tar.bz2",
                        "libksba-1.6.8.tar.bz2", "npth-1.8.tar.bz2", "gnupg-2.4.9.tar.bz2",
                    ).map { sourcesDir.file(it) },
                )
                sourceLock.set(sourcesDir.file("SOURCES.lock"))
                libgpgErrorPatch.set(runtimeDir.file("patches/libgpg-error/0001-skip-deprecated-config-self-test.patch"))
                this.jobs.set(jobs)
                outputFile.set(binariesDir.map { it.file("${abi.id}/gpgv") })
            },
            compile(
                "pkgdetails", listOf(extractPkgdetails.map { it.outputFile }),
                listOf("-std=c17", "-Os", "-fPIE", "-fstack-protector-strong", "-Wall", "-Wextra", "-Wformat=2", "-Wno-format-nonliteral", "-Wl,-pie,-z,relro,-z,now"),
                emptyList(), ElfKind.DYNAMIC,
            ),
            compile("bfu-namespace-probe", listOf(cpp("bfu_namespace_probe.c")), hardened, emptyList(), ElfKind.DYNAMIC),
            compile("dawnshell-fdguard", listOf(cpp("dawnshell_fdguard.c")), hardened, emptyList(), ElfKind.DYNAMIC),
            compile(
                "dawnshell-codec", listOf(cpp("dawnshell_codec_client.c")),
                listOf("-std=c17", "-Os", "-static", "-fPIE", "-fstack-protector-strong", "-Wall", "-Wextra", "-Werror", "-Wformat=2", "-Wl,-z,relro,-z,now", "-Wl,--gc-sections"),
                emptyList(), ElfKind.STATIC,
            ),
            compile(
                "dawnshell-codec-worker", listOf(cpp("dawnshell_codec_worker.c"), cpp("dawnshell_codec_ndk.c")),
                hardened, listOf("-lmediandk", "-landroid", "-ldl"), ElfKind.CODEC_WORKER,
            ),
        )
    }

    return tasks.register("assembleBfuRuntimeAssets", BfuRuntimeAssetsTask::class.java) {
        group = "bfu runtime"
        description = "Builds every bundled native program for all configured ABIs and stages them as assets."
        dependsOn(allTasks)
        binariesDirectory.set(binariesDir)
        this.abiIds.set(abiIds)
        this.androidApi.set(androidApi)
    }
}
