package me.aroxu.dawnshell

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StatusStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun roundTripsStateSummaryAndDetail() {
        val file = folder.newFile("a.status")
        StatusStore.write(file, "SUCCEEDED command=x exit=0", NativeOutput.detail("""{"v":1,"event":"running"}"""))
        val status = StatusStore.read(file)!!
        assertEquals(OperationState.SUCCEEDED, status.state)
        assertEquals("command=x exit=0", status.summary)
        assertEquals(1, (status.detail as JsonObject).size)
        assertEquals("SUCCEEDED command=x exit=0", StatusStore.readText(file))
    }

    @Test
    fun missingFileIsNull() {
        assertNull(StatusStore.read(folder.root.resolve("none.status")))
        assertEquals("", StatusStore.readText(folder.root.resolve("none.status")))
    }

    @Test
    fun unknownLeadingWordIsKeptAsSummary() {
        val file = folder.newFile("b.status")
        StatusStore.write(file, "UNAVAILABLE user_unlocked=false")
        assertEquals(OperationState.UNAVAILABLE, StatusStore.read(file)!!.state)
        StatusStore.write(file, "weird text")
        val status = StatusStore.read(file)!!
        assertEquals(OperationState.UNKNOWN, status.state)
        assertEquals("weird text", status.summary)
    }

    @Test
    fun readsLegacyPlainStatusFiles() {
        val file = folder.newFile("c.status")
        file.writeText("1700000000000 FAILED stage=boom\n")
        val status = StatusStore.read(file)!!
        assertEquals(OperationState.FAILED, status.state)
        assertEquals("stage=boom", status.summary)
    }
}
