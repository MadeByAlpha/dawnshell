package me.aroxu.dawnshell.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayInputStream

/** Cross-builds the BusyBox toolbox from the pinned source, Android patch and config fragment. */
@CacheableTask
abstract class BusyBoxTask : BfuNativeTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceArchive: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceLock: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val patchFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val configFragment: RegularFileProperty

    @get:Internal
    abstract val jobs: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val requiredSymbols = listOf(
        "BUSYBOX", "FEATURE_INSTALLER", "FEATURE_MD5_SHA1_SUM_CHECK", "FEATURE_STAT_FORMAT", "ASH", "AWK",
        "CHROOT", "DPKG_DEB", "MOUNT", "SED", "SHA256SUM", "STAT", "TAR", "UNSHARE", "WGET", "ZCAT", "XZCAT",
    )

    @TaskAction
    fun build() {
        val archive = sourceArchive.get().asFile
        SourceLock.verify(sourceLock.get().asFile, archive)
        val tools = ndk
        val work = cleanWorkDirectory()
        runner.run(work, "tar", "-xf", archive.path, "-C", work.path)
        val source = work.resolve(archive.name.removeSuffix(".tar.bz2"))
        runner.run(source, "patch", "-p1", "-i", patchFile.get().asFile.path)
        mergeConfig(source)

        runner.run(
            source, "make", "-j${jobs.get()}",
            "CC=${tools.cc}", "HOSTCC=gcc",
            "AR=${tools.ar}", "NM=${tools.nm}", "STRIP=${tools.strip}", "OBJCOPY=${tools.objcopy}",
            "EXTRA_CFLAGS=-Wno-ignored-optimization-argument -Wno-unused-command-line-argument",
        )
        runner.run(source, tools.strip.path, "--strip-unneeded", "busybox")
        installExecutable(source.resolve("busybox"), outputFile.get().asFile)

        val config = source.resolve(".config").readLines()
        requiredSymbols.forEach { symbol ->
            if ("CONFIG_$symbol=y" !in config) {
                throw GradleException("BusyBox output is missing required config symbol: CONFIG_$symbol=y")
            }
        }
        ElfValidator(runner, tools).validate(outputFile.get().asFile, ElfKind.DYNAMIC)
    }

    private fun mergeConfig(source: java.io.File) {
        runner.run(source, "make", "allnoconfig")
        val configFile = source.resolve(".config")
        val lines = configFile.readLines().toMutableList()
        configFragment.get().asFile.readLines()
            .filter { it.startsWith("CONFIG_") && it.endsWith("=y") }
            .forEach { setting ->
                val unset = lines.indexOf("# ${setting.substringBefore('=')} is not set")
                if (unset >= 0) lines[unset] = setting else if (setting !in lines) lines += setting
            }
        configFile.writeText(lines.joinToString("\n", postfix = "\n"))
        // oldconfig asks about every symbol the fragment did not decide; answer with the defaults.
        runner.run(source, "make", "oldconfig", stdin = ByteArrayInputStream("\n".repeat(1 shl 16).toByteArray()))
    }
}
