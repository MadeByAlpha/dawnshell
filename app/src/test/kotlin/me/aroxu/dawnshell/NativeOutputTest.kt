package me.aroxu.dawnshell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOutputTest {
    private val running = """{"v":1,"ts":1700000000,"event":"running","state":"running","mode":"systemd","supervisor_pid":812,"init_host_pid":840,"supervisor_identity_valid":true,"init_identity_valid":true,"namespace_topology_valid":true,"ipc_namespace":"android-shared","network_namespace":"android-shared","network_mode":"shared-nic","cgroup_mode":"v2","host_usb_mode":"off","ns":{"pid":4026532600,"mnt":4026532598,"uts":4026532599,"ipc":4026531839,"cgroup":4026532601,"net":4026532001},"updated_epoch":1699999990}"""
    private val health = """{"v":1,"ts":1700000000,"event":"health","mode":"systemd","pid1":"systemd","system_state":"running","dbus_service":"active","dbus_bus":"ok","ssh_service":"active","boot_proof_service":"active","boot_proof_marker":"present","default_target":"multi-user.target","target_state":"active","cgroup_mode":"v2","cgroup_delegation":"delegated","devices_cgroup":"bpf","devices_path":"","systemd_path":"","unified_path":"/","pid1_start_ticks":0,"devices_hierarchy":0,"listen_22":true,"healthy":true}"""

    @Test
    fun decodesRunningWithNamespaces() {
        val event = NativeOutput.parse(running).single() as LauncherRunning
        assertTrue(event.isRunning)
        assertEquals(812, event.supervisorPid)
        assertEquals("v2", event.cgroupMode)
        assertEquals(4026532600L, event.ns.pid)
    }

    @Test
    fun startingKeepsItsWireName() {
        val event = NativeOutput.parse(running.replace("\"event\":\"running\"", "\"event\":\"starting_or_unknown\"")).single()
        assertFalse((event as LauncherRunning).isRunning)
        assertEquals("starting_or_unknown", event.event)
    }

    @Test
    fun decodesHealthAndIgnoresUnknownFields() {
        val event = NativeOutput.health("noise\n$health\n")
        assertNotNull(event)
        assertTrue(event!!.healthy)
        assertTrue(event.listen22)
        assertEquals("multi-user.target", event.defaultTarget)
    }

    @Test
    fun skipsPlainLogLinesAndMalformedJson() {
        val text = "[1700000000] BFU_DEBIAN_STAGE private_proc_mounted\n{\"v\":1,\"event\":\n$running\n"
        assertEquals(1, NativeOutput.parse(text).size)
        assertNull(NativeOutput.health(text))
    }

    @Test
    fun keepsUnknownEventsAsOther() {
        val event = NativeOutput.parse("""{"v":2,"ts":1,"event":"future_event","x":1}""").single()
        assertTrue(event is OtherEvent)
        assertEquals("future_event", event.event)
    }

    @Test
    fun decodesFailureAndStartFailure() {
        val text = """{"v":1,"ts":1,"event":"failed","stage":"root_lstat","errno":2,"error":"No such file or directory"}
{"v":1,"ts":1,"event":"start_failed","stage":"namespace_setup","exit":60}"""
        val events = NativeOutput.parse(text)
        assertEquals(2, events.size)
        assertEquals(2, (events[0] as Failed).errno)
        assertEquals(60, (events[1] as StartFailed).exit)
        assertNull((events[1] as StartFailed).errno)
    }

    @Test
    fun runtimeProbeResult() {
        assertTrue(NativeOutput.runtimeProbeSucceeded("""{"v":1,"ts":1,"event":"namespace_ok","pid":1,"proc1":"sh","arch":"arm64","debian":13,"init":"present","systemctl":"present","cgroup":"0::/,"}"""))
        assertFalse(NativeOutput.runtimeProbeSucceeded("""{"v":1,"ts":1,"event":"namespace_failed","stage":"dpkg_arch"}"""))
    }

    @Test
    fun detailKeepsNewestEvents() {
        val text = (1..10).joinToString("\n") { """{"v":1,"ts":$it,"event":"stage$it"}""" }
        val events = NativeOutput.detail(text)!!["events"] as kotlinx.serialization.json.JsonArray
        assertEquals(8, events.size)
        assertNull(NativeOutput.detail("no json here"))
    }
}
