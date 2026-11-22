package me.aroxu.dawnshell

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One JSON Lines event emitted by the native helpers; schema v1 is described in docs/native-output-schema.md. */
sealed interface NativeEvent {
    val event: String
}

@Serializable
data class Namespaces(
    val pid: Long = 0,
    val mnt: Long = 0,
    val uts: Long = 0,
    val ipc: Long = 0,
    val cgroup: Long = 0,
    val net: Long = 0,
)

/** The supervisor's lock is held: `running` once its identity is proven, otherwise `starting_or_unknown`. */
@Serializable
data class LauncherRunning(
    override val event: String = "running",
    val state: String = "",
    val mode: String = "",
    @SerialName("supervisor_pid") val supervisorPid: Int = 0,
    @SerialName("init_host_pid") val initHostPid: Int = 0,
    @SerialName("supervisor_identity_valid") val supervisorIdentityValid: Boolean = false,
    @SerialName("init_identity_valid") val initIdentityValid: Boolean = false,
    @SerialName("namespace_topology_valid") val namespaceTopologyValid: Boolean = false,
    @SerialName("cgroup_mode") val cgroupMode: String = "",
    @SerialName("host_usb_mode") val hostUsbMode: String = "",
    val ns: Namespaces = Namespaces(),
    @SerialName("updated_epoch") val updatedEpoch: Long = 0,
) : NativeEvent {
    val isRunning get() = event == "running"
}

@Serializable
data class LauncherStopped(
    override val event: String = "stopped",
    @SerialName("last_state") val lastState: String = "",
    val mode: String = "",
    @SerialName("cgroup_mode") val cgroupMode: String = "",
    @SerialName("host_usb_mode") val hostUsbMode: String = "",
    @SerialName("wait_status") val waitStatus: Int = -1,
    @SerialName("updated_epoch") val updatedEpoch: Long = 0,
) : NativeEvent

/** `started` (systemd) or `fallback_started` (direct sshd). */
@Serializable
data class LauncherStarted(
    override val event: String = "started",
    @SerialName("supervisor_pid") val supervisorPid: Int = 0,
    @SerialName("init_host_pid") val initHostPid: Int = 0,
    @SerialName("sshd_pid") val sshdPid: Int = 0,
    val mode: String = "systemd",
    @SerialName("cgroup_mode") val cgroupMode: String = "",
    @SerialName("host_usb_mode") val hostUsbMode: String = "",
    @SerialName("host_usb_filter_count") val hostUsbFilterCount: Int = 0,
) : NativeEvent

@Serializable
data class StartFailed(
    override val event: String = "start_failed",
    val stage: String = "",
    val errno: Int? = null,
    val exit: Int? = null,
    @SerialName("child_exit") val childExit: Int? = null,
    @SerialName("child_signal") val childSignal: Int? = null,
) : NativeEvent

@Serializable
data class Failed(
    override val event: String = "failed",
    val stage: String = "",
    val errno: Int? = null,
    val error: String = "",
) : NativeEvent

/** Health of the systemd instance (`mode=systemd`) or of the direct sshd fallback (`mode=compat`). */
@Serializable
data class Health(
    override val event: String = "health",
    val mode: String = "systemd",
    val healthy: Boolean = false,
    val pid1: String = "",
    @SerialName("system_state") val systemState: String = "",
    @SerialName("dbus_service") val dbusService: String = "",
    @SerialName("dbus_bus") val dbusBus: String = "",
    @SerialName("ssh_service") val sshService: String = "",
    @SerialName("boot_proof_service") val bootProofService: String = "",
    @SerialName("boot_proof_marker") val bootProofMarker: String = "",
    @SerialName("default_target") val defaultTarget: String = "",
    @SerialName("target_state") val targetState: String = "",
    @SerialName("listen_22") val listen22: Boolean = false,
    @SerialName("compat_ready") val compatReady: Boolean = false,
    @SerialName("cgroup_mode") val cgroupMode: String = "",
    @SerialName("cgroup_delegation") val cgroupDelegation: String = "",
    @SerialName("devices_cgroup") val devicesCgroup: String = "",
) : NativeEvent

@Serializable
data class Diagnostic(
    override val event: String = "diagnostic",
    val label: String = "",
    val content: String = "",
) : NativeEvent

/** Result of the in-chroot runtime probe: `namespace_ok`/`compat_ok` succeed, `*_failed` carry the failed stage. */
@Serializable
data class RuntimeProbe(
    override val event: String = "",
    val stage: String = "",
    val arch: String = "",
    val debian: Int = 0,
    val init: String = "",
    val systemctl: String = "",
) : NativeEvent {
    val succeeded get() = event == "namespace_ok" || event == "compat_ok"
}

/** Events without a dedicated type (and events newer than this app) keep their raw fields. */
data class OtherEvent(override val event: String, val fields: JsonObject) : NativeEvent

object NativeOutput {
    private const val MAX_DETAIL_EVENTS = 8
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val serializers: Map<String, KSerializer<out NativeEvent>> = mapOf(
        "running" to LauncherRunning.serializer(),
        "starting_or_unknown" to LauncherRunning.serializer(),
        "stopped" to LauncherStopped.serializer(),
        "started" to LauncherStarted.serializer(),
        "fallback_started" to LauncherStarted.serializer(),
        "start_failed" to StartFailed.serializer(),
        "failed" to Failed.serializer(),
        "health" to Health.serializer(),
        "diagnostic" to Diagnostic.serializer(),
        "namespace_ok" to RuntimeProbe.serializer(),
        "namespace_failed" to RuntimeProbe.serializer(),
        "compat_ok" to RuntimeProbe.serializer(),
        "compat_failed" to RuntimeProbe.serializer(),
    )

    /** Decodes every JSON line; non-JSON lines such as free-form stderr logs and malformed lines are skipped. */
    @JvmStatic
    fun parse(text: String?): List<NativeEvent> =
        text.orEmpty().lineSequence().map(String::trim).filter { it.startsWith("{") }.mapNotNull(::decode).toList()

    /** Raw events (newest $MAX_DETAIL_EVENTS) for the persisted status detail; null when there are none. */
    @JvmStatic
    fun detail(text: String?): JsonObject? {
        val events = text.orEmpty().lineSequence().map(String::trim).filter { it.startsWith("{") }
            .mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            .toList().takeLast(MAX_DETAIL_EVENTS)
        return if (events.isEmpty()) null else JsonObject(mapOf("events" to JsonArray(events)))
    }

    @JvmStatic
    fun health(text: String?): Health? = parse(text).filterIsInstance<Health>().lastOrNull()

    @JvmStatic
    fun has(text: String?, event: String): Boolean = parse(text).any { it.event == event }

    @JvmStatic
    fun runtimeProbeSucceeded(text: String?): Boolean =
        parse(text).filterIsInstance<RuntimeProbe>().any { it.succeeded }

    /** Typed view of the events persisted by [detail]. */
    @JvmStatic
    fun fromDetail(detail: JsonObject?): List<NativeEvent> =
        (detail?.get("events") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::decodeFields) }

    private fun decode(line: String): NativeEvent? = try {
        decodeFields(json.parseToJsonElement(line).jsonObject)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun decodeFields(fields: JsonObject): NativeEvent? = try {
        val name = fields["event"]?.jsonPrimitive?.content
        when (val serializer = name?.let(serializers::get)) {
            null -> name?.let { OtherEvent(it, fields) }
            else -> json.decodeFromJsonElement(serializer, fields).withEvent(name)
        }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** One serializer serves several event names, so the decoded event name is the wire name. */
    private fun NativeEvent.withEvent(name: String): NativeEvent = when (this) {
        is LauncherRunning -> copy(event = name)
        is LauncherStarted -> copy(event = name)
        is RuntimeProbe -> copy(event = name)
        else -> this
    }
}
