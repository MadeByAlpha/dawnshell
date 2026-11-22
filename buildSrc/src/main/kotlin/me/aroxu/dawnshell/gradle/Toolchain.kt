package me.aroxu.dawnshell.gradle

import org.gradle.api.GradleException
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

enum class Abi(
    val id: String,
    private val clangTriple: String,
    val gnuHost: String,
    val debianArch: String,
    val machine: String,
    val interpreter: String,
) {
    ARMEABI_V7A("armeabi-v7a", "armv7a-linux-android", "armv7a-linux-androideabi", "armhf", "ARM", "/system/bin/linker"),
    ARM64_V8A("arm64-v8a", "aarch64-linux-android", "aarch64-linux-androideabi", "arm64", "AArch64", "/system/bin/linker64"),
    X86_64(
        "x86_64", "x86_64-linux-android", "x86_64-linux-androideabi", "amd64",
        "Advanced Micro Devices X86-64", "/system/bin/linker64",
    );

    fun clangTarget(api: Int) = "$clangTriple$api"

    val taskSuffix get() = id.split('-', '_').joinToString("") { it.replaceFirstChar(Char::uppercase) }

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: throw GradleException("Unsupported ABI: $id")
    }
}

val isWindowsHost = System.getProperty("os.name").startsWith("Windows")

class NdkTools(ndkDir: File, val abi: Abi, val api: Int) {
    private val binDir = ndkDir.resolve("toolchains/llvm/prebuilt/${hostTag()}/bin")

    val clang = tool("clang")
    val ar = tool("llvm-ar")
    val nm = tool("llvm-nm")
    val ranlib = tool("llvm-ranlib")
    val strip = tool("llvm-strip")
    val objcopy = tool("llvm-objcopy")
    val readelf = tool("llvm-readelf")
    val strings = tool("llvm-strings")

    val clangArguments = listOf(clang.path, "--target=${abi.clangTarget(api)}")
    val cc get() = clangArguments.joinToString(" ")

    private fun hostTag(): String {
        val os = System.getProperty("os.name")
        return when {
            os.startsWith("Linux") -> "linux-x86_64"
            os.startsWith("Mac") -> "darwin-x86_64"
            os.startsWith("Windows") -> "windows-x86_64"
            else -> throw GradleException("Unsupported build host: $os")
        }
    }

    private fun tool(name: String) = listOf(name, "$name.exe", "$name.cmd").map { binDir.resolve(it) }
        .firstOrNull { it.isFile } ?: throw GradleException("Missing NDK tool: $name in $binDir")
}

class CommandRunner(private val exec: ExecOperations, private val baseEnvironment: Map<String, String>) {
    fun run(
        workDir: File?,
        vararg command: String,
        environment: Map<String, String> = emptyMap(),
        stdin: InputStream? = null,
    ): String {
        val output = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine(*command)
            workDir?.let { workingDir = it }
            environment(baseEnvironment + environment)
            stdin?.let { standardInput = it }
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8)
        if (result.exitValue != 0) {
            throw GradleException(
                "Command failed with exit code ${result.exitValue}: ${command.joinToString(" ")}\n" +
                    text.lines().takeLast(200).joinToString("\n"),
            )
        }
        return text
    }
}

enum class ElfKind { DYNAMIC, STATIC, CODEC_WORKER }

/** Mirrors the former validate_elf, validate_static_elf and validate_codec_worker_elf shell checks. */
class ElfValidator(private val runner: CommandRunner, private val ndk: NdkTools) {
    private val fixedDataPath = Regex("/data/(data|user|user_de)/[^/ ]+")
    private val hostPath = Regex("[A-Za-z]:/Users/")
    private val workDirectory = Regex("dawnshell-bootstrap-runtime")

    fun validate(binary: File, kind: ElfKind) {
        val header = runner.run(null, ndk.readelf.path, "-h", binary.path)
        val program = runner.run(null, ndk.readelf.path, "-l", binary.path)
        val dynamic = runner.run(null, ndk.readelf.path, "-d", binary.path)
        val libraries = Regex("""Shared library: \[([^\]]*)]""").findAll(dynamic).map { it.groupValues[1] }.toSet()
        val machine = Regex("Machine:\\s+" + Regex.escape(ndk.abi.machine))

        require(machine.containsMatchIn(header), binary) { "unexpected machine type" }
        when (kind) {
            ElfKind.STATIC -> {
                require(Regex("Type:\\s+EXEC").containsMatchIn(header), binary) { "not a static EXEC" }
                require("Requesting program interpreter" !in program, binary) { "unexpected program interpreter" }
                require(libraries.isEmpty(), binary) { "unexpected shared dependencies $libraries" }
            }
            ElfKind.DYNAMIC, ElfKind.CODEC_WORKER -> {
                require(Regex("Type:\\s+DYN").containsMatchIn(header), binary) { "not a PIE DYN" }
                require(ndk.abi.interpreter in program, binary) { "missing interpreter ${ndk.abi.interpreter}" }
                val allowed = if (kind == ElfKind.DYNAMIC) {
                    setOf("libc.so", "libdl.so")
                } else {
                    setOf("libandroid.so", "libc.so", "libdl.so", "libmediandk.so")
                }
                require(if (kind == ElfKind.DYNAMIC) allowed.containsAll(libraries) else libraries == allowed, binary) {
                    "unexpected Android native dependencies $libraries"
                }
            }
        }

        val strings = runner.run(null, ndk.strings.path, binary.path).lines()
        require(strings.none { fixedDataPath.containsMatchIn(it) }, binary) { "fixed Android data path embedded" }
        require(strings.none { hostPath.containsMatchIn(it) }, binary) { "host build path embedded" }
        if (kind == ElfKind.DYNAMIC) {
            require(strings.none { workDirectory.containsMatchIn(it) }, binary) { "non-reproducible build path embedded" }
        }
    }

    private fun require(condition: Boolean, binary: File, message: () -> String) {
        if (!condition) throw GradleException("ELF validation failed for ${binary.name}: ${message()}")
    }
}
