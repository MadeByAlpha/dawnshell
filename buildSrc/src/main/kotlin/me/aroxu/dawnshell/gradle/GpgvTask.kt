package me.aroxu.dawnshell.gradle

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Cross-builds gpgv and its static library chain (libgpg-error, libgcrypt, libassuan, libksba, npth). */
@CacheableTask
abstract class GpgvTask : BfuNativeTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceArchives: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sourceLock: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val libgpgErrorPatch: RegularFileProperty

    @get:Internal
    abstract val jobs: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private lateinit var work: File
    private lateinit var prefix: File
    private lateinit var toolEnvironment: Map<String, String>

    @TaskAction
    fun build() {
        SourceLock.verify(sourceLock.get().asFile, *sourceArchives.files.toTypedArray())
        val tools = ndk
        work = cleanWorkDirectory()
        prefix = work.resolve("prefix").apply { mkdirs() }
        sourceArchives.files.forEach { runner.run(work, "tar", "-xf", it.path, "-C", work.path) }
        runner.run(sourceDir("libgpg-error"), "patch", "-p1", "-i", libgpgErrorPatch.get().asFile.path)

        toolEnvironment = mapOf(
            "CC" to tools.cc, "AR" to tools.ar.path, "NM" to tools.nm.path,
            "RANLIB" to tools.ranlib.path, "STRIP" to tools.strip.path,
            "CPPFLAGS" to "-I$prefix/include",
            "CFLAGS" to "-Os -fPIC -fstack-protector-strong",
            "LDFLAGS" to "-L$prefix/lib -Wl,-z,relro,-z,now",
            "PKG_CONFIG_LIBDIR" to "$prefix/lib/pkgconfig",
            "PKG_CONFIG_PATH" to "$prefix/lib/pkgconfig",
            "PATH" to "$prefix/bin${File.pathSeparator}${System.getenv("PATH")}",
        )

        configureStaticLibrary(
            "libgpg-error", "--disable-nls", "--disable-doc", "--disable-tests", "--disable-install-gpg-error-config",
        )
        configureStaticLibrary("libgcrypt", "--disable-doc", "--disable-asm", "--with-libgpg-error-prefix=$prefix")
        configureStaticLibrary("libassuan", "--disable-doc", "--with-libgpg-error-prefix=$prefix")
        configureStaticLibrary("libksba", "--disable-doc", "--with-libgpg-error-prefix=$prefix")
        buildNpth()
        // gpgrt-config recursively invokes itself under MSYS path translation. The legacy, generated
        // config scripts are sufficient for this cross-build.
        prefix.resolve("bin/gpgrt-config").delete()
        val gpgv = buildGnupg(tools)

        installExecutable(gpgv, outputFile.get().asFile)
        ElfValidator(runner, tools).validate(outputFile.get().asFile, ElfKind.DYNAMIC)
    }

    private fun sourceDir(name: String) = work.listFiles { file -> file.name.startsWith("$name-") }!!.single()

    private fun buildTriplet(source: File) = runner.run(source, source.resolve("build-aux/config.guess").path).trim()

    private fun hostConfigureArguments(source: File) =
        listOf("--host=${abi.gnuHost}", "--build=${buildTriplet(source)}")

    private fun configureStaticLibrary(name: String, vararg arguments: String) {
        val source = sourceDir(name)
        // Keep runtime directory macros deterministic under MSYS path translation.
        val msys = if (isWindowsHost) mapOf("MSYS2_ARG_CONV_EXCL" to "-DLOCALEDIR;-DSYSCONFDIR;-DLOCALSTATEDIR;-DDATADIR") else emptyMap()
        val environment = toolEnvironment + msys
        runner.run(
            source, "./configure", *hostConfigureArguments(source).toTypedArray(),
            "--prefix=$prefix", "--enable-static", "--disable-shared", *arguments, environment = environment,
        )
        // Runtime directory constants must not contain the temporary build prefix. Override them only while
        // compiling; make install keeps using the private staging prefix and never writes to host /system.
        runner.run(
            source, "make", "-j${jobs.get()}",
            "localedir=/system/share/locale", "sysconfdir=/system/etc",
            "localstatedir=/system/var", "datadir=/system/share",
            environment = environment,
        )
        runner.run(source, "make", "install", environment = environment)
    }

    private fun buildNpth() {
        val source = sourceDir("npth")
        val environment = toolEnvironment + ("ac_cv_search_pthread_cancel" to "none required")
        runner.run(
            source, "./configure", *hostConfigureArguments(source).toTypedArray(),
            "--prefix=$prefix", "--enable-static", "--disable-shared", "--disable-tests",
            environment = environment,
        )
        runner.run(source, "make", "-j${jobs.get()}", environment = toolEnvironment)
        runner.run(source, "make", "install", environment = toolEnvironment)
    }

    private fun buildGnupg(tools: NdkTools): File {
        val source = sourceDir("gnupg")
        // Preserve the fixed /system paths embedded by GnuPG instead of letting MSYS rewrite -D arguments.
        val msys = if (isWindowsHost) mapOf("MSYS2_ARG_CONV_EXCL" to "-DGNUPG_;-DLOCALEDIR") else emptyMap()
        val environment = toolEnvironment + msys + mapOf(
            "CFLAGS" to "-Os -fPIE -fstack-protector-strong",
            "GPG_ERROR_CONFIG" to sourceDir("libgpg-error").resolve("src/gpg-error-config-old").path,
            "LIBGCRYPT_CONFIG" to "$prefix/bin/libgcrypt-config",
            "LIBASSUAN_CONFIG" to "$prefix/bin/libassuan-config",
            "KSBA_CONFIG" to "$prefix/bin/ksba-config",
            "NPTH_CONFIG" to sourceDir("npth").resolve("npth-config").path,
        )
        val disabled = listOf(
            "nls", "doc", "tests", "gpgsm", "scdaemon", "dirmngr", "keyboxd", "tpm2d", "gpgtar", "wks-tools",
            "ccid-driver", "ldap", "zip", "bzip2", "sqlite",
        ).map { "--disable-$it" }
        runner.run(
            source, "./configure", *hostConfigureArguments(source).toTypedArray(),
            "--prefix=/system", *disabled.toTypedArray(),
            "--without-libiconv-prefix", "--without-libintl-prefix",
            "--with-libgpg-error-prefix=$prefix", "--with-libgcrypt-prefix=$prefix", "--with-libassuan-prefix=$prefix",
            environment = environment,
        )
        listOf("common", "regexp", "kbx").forEach {
            runner.run(source, "make", "-j${jobs.get()}", "-C", it, environment = environment)
        }
        runner.run(source, "make", "-j${jobs.get()}", "-C", "g10", "gpgv", environment = environment)
        runner.run(source, tools.strip.path, "--strip-unneeded", "g10/gpgv")
        return source.resolve("g10/gpgv")
    }
}
