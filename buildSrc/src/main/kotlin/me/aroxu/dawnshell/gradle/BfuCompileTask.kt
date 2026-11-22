package me.aroxu.dawnshell.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Compiles one DawnShell-owned C program with the pinned NDK clang, strips it and validates the ELF. */
@CacheableTask
abstract class BfuCompileTask : BfuNativeTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val headers: ConfigurableFileCollection

    @get:Input
    abstract val compilerArguments: ListProperty<String>

    @get:Input
    abstract val linkerArguments: ListProperty<String>

    @get:Input
    abstract val elfKind: Property<ElfKind>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun compile() {
        val tools = ndk
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        runner.run(
            null,
            *tools.clangArguments.toTypedArray(),
            *compilerArguments.get().toTypedArray(),
            *sources.files.map { it.path }.toTypedArray(),
            *linkerArguments.get().toTypedArray(),
            "-o", output.path,
        )
        runner.run(null, tools.strip.path, "--strip-unneeded", output.path)
        makePrivateExecutable(output)
        ElfValidator(runner, tools).validate(output, elfKind.get())
    }
}
