package me.aroxu.dawnshell.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Lays the per-ABI binaries out as assets/bfu/bin/<abi>/ and writes each ABI's runtime.properties. */
abstract class BfuRuntimeAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binariesDirectory: DirectoryProperty

    @get:Input
    abstract val abiIds: ListProperty<String>

    @get:Input
    abstract val androidApi: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun assemble() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        abiIds.get().map { Abi.of(it) }.forEach { abi ->
            val target = root.resolve("bfu/bin/${abi.id}")
            binariesDirectory.get().asFile.resolve(abi.id).copyRecursively(target)
            target.resolve("runtime.properties").writeText(
                listOf(
                    "android_abi=${abi.id}",
                    "debian_architecture=${abi.debianArch}",
                    "android_api=${androidApi.get()}",
                    "busybox=1.38.0",
                    "pkgdetails_base_installer=1.226",
                    "gpgv=2.4.9",
                    "dawnshell_codec_protocol=1",
                    "dawnshell_codec_transport=inherited_memfd_eventfd",
                    "dawnshell_codec_worker=ndk_mediacodec",
                ).joinToString("\n", postfix = "\n"),
            )
        }
    }
}
