package me.aroxu.dawnshell.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/** Extracts one verified file from a pinned upstream archive (Gradle's tarTree cannot read xz). */
@CacheableTask
abstract class ExtractArchiveMemberTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val archive: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceLock: RegularFileProperty

    @get:Input
    abstract val member: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun extract() {
        SourceLock.verify(sourceLock.get().asFile, archive.get().asFile)
        outputFile.get().asFile.outputStream().use { output ->
            execOperations.exec {
                commandLine("tar", "-xOf", archive.get().asFile.path, member.get())
                standardOutput = output
            }
        }
    }
}
