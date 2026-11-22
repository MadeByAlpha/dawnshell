package me.aroxu.dawnshell.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import javax.inject.Inject

abstract class BfuNativeTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val abiId: Property<String>

    @get:Input
    abstract val androidApi: Property<Int>

    @get:Input
    abstract val sourceDateEpoch: Property<String>

    @get:Internal
    protected val abi get() = Abi.of(abiId.get())

    @get:Internal
    protected val ndk get() = NdkTools(ndkDirectory.get().asFile, abi, androidApi.get())

    @get:Internal
    protected val runner
        get() = CommandRunner(
            execOperations,
            mapOf("LC_ALL" to "C", "TZ" to "UTC", "SOURCE_DATE_EPOCH" to sourceDateEpoch.get()),
        )

    protected fun cleanWorkDirectory(): File = temporaryDir.resolve("work").apply {
        deleteRecursively()
        mkdirs()
    }

    protected fun installExecutable(source: File, target: File) {
        target.parentFile.mkdirs()
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        makePrivateExecutable(target)
    }

    protected fun makePrivateExecutable(file: File) {
        if (!isWindowsHost) Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwx------"))
    }
}
