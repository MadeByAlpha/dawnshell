package me.aroxu.dawnshell

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException

enum class OperationState { QUEUED, RUNNING, SUCCEEDED, FAILED, REJECTED, UNAVAILABLE, UNKNOWN }

/** Persisted result of one background operation; `detail` carries typed native events when there are any. */
@Serializable
data class OperationStatus(
    val v: Int = 1,
    val ts: Long = 0,
    val state: OperationState = OperationState.UNKNOWN,
    val summary: String = "",
    val detail: JsonObject? = null,
) {
    /** Legacy one-line form, shown in logs. */
    fun text() = "${state.name} $summary"
}

/** Reads and atomically writes the *.status files as one JSON object each. */
object StatusStore {
    private const val MAX_BYTES = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val lock = Any()

    /** `text` is "STATE summary"; an unknown leading word is kept as UNKNOWN with the whole text as summary. */
    @JvmStatic
    @JvmOverloads
    @Throws(IOException::class)
    fun write(destination: File, text: String, detail: JsonObject? = null) {
        val separator = text.indexOf(' ')
        val word = if (separator < 0) text else text.substring(0, separator)
        val state = OperationState.entries.firstOrNull { it.name == word }
        val status = OperationStatus(
            ts = System.currentTimeMillis(),
            state = state ?: OperationState.UNKNOWN,
            summary = if (state == null) text else if (separator < 0) "" else text.substring(separator + 1),
            detail = detail,
        )
        val temporary = File(destination.parentFile, destination.name + ".new")
        synchronized(lock) {
            temporary.outputStream().use { output ->
                output.write((json.encodeToString(OperationStatus.serializer(), status) + "\n").toByteArray())
                output.fd.sync()
            }
            ownerOnly(temporary)
            if (destination.exists() && !destination.delete()) throw IOException("cannot replace " + destination.name)
            if (!temporary.renameTo(destination)) throw IOException("cannot publish " + destination.name)
            ownerOnly(destination)
        }
    }

    /** Null when the file does not exist yet. A pre-JSON "<millis> STATE summary" file is still understood. */
    @JvmStatic
    @Throws(IOException::class)
    fun read(source: File): OperationStatus? {
        if (!source.isFile) return null
        val text = source.inputStream().use { it.readNBytes(MAX_BYTES) }.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) return null
        try {
            return json.decodeFromString(OperationStatus.serializer(), text)
        } catch (e: SerializationException) {
            val legacy = text.substringAfter(' ', text)
            val word = legacy.substringBefore(' ')
            val state = OperationState.entries.firstOrNull { it.name == word } ?: OperationState.UNKNOWN
            return OperationStatus(state = state, summary = if (state == OperationState.UNKNOWN) text else legacy.substringAfter(' ', ""))
        }
    }

    /** Empty string when there is no status yet; used by the log screens. */
    @JvmStatic
    @Throws(IOException::class)
    fun readText(source: File): String = read(source)?.text().orEmpty()

    private fun ownerOnly(file: File) {
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }
}
