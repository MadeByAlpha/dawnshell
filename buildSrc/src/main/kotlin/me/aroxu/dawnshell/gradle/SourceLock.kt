package me.aroxu.dawnshell.gradle

import org.gradle.api.GradleException
import java.io.File
import java.security.MessageDigest

object SourceLock {
    /** Checks archives against the SHA-256 recorded in bfu-runtime/sources/SOURCES.lock. */
    fun verify(lock: File, vararg archives: File) {
        val hashes = lock.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(Regex("\\s+")) }.associate { it[1] to it[0] }
        archives.forEach { archive ->
            val expected = hashes[archive.name] ?: throw GradleException("${archive.name} is not listed in ${lock.name}")
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) throw GradleException("SHA-256 mismatch for ${archive.name}: $actual != $expected")
        }
    }
}
