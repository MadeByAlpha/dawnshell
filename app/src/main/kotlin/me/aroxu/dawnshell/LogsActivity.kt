package me.aroxu.dawnshell

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import me.aroxu.dawnshell.ui.DawnShellTheme
import me.aroxu.dawnshell.ui.LogEntry
import me.aroxu.dawnshell.ui.LogsScreen

/** Live index of the separate DawnShell log streams. */
class LogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DawnShellTheme {
                LogsScreen(
                    entries = LOG_TYPES.map { LogEntry(it, DawnShellLogRepository.titleRes(it), DawnShellLogRepository.descriptionRes(it)) },
                    readSummary = ::readSummary,
                    onBack = ::finish,
                    onOpen = { type -> startActivity(LogDetailActivity.createIntent(this, type)) },
                )
            }
        }
    }

    private fun readSummary(type: String): String = try {
        DawnShellLogRepository.readSummary(this, type)
    } catch (e: IOException) {
        getString(R.string.dawnshell_log_read_failed, BfuSu.sanitize(e.message))
    }

    private companion object {
        val LOG_TYPES = listOf(
            DawnShellLogRepository.OPERATIONS,
            DawnShellLogRepository.INSTALLATION,
            DawnShellLogRepository.CONFIGURATION,
            DawnShellLogRepository.COMPATIBILITY,
            DawnShellLogRepository.HARDWARE_CODEC,
            DawnShellLogRepository.CODEC_LONG_RUN,
            DawnShellLogRepository.LIFECYCLE,
            DawnShellLogRepository.DIAGNOSTICS,
        )
    }
}
