package me.aroxu.dawnshell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import me.aroxu.dawnshell.ui.DawnShellTheme
import me.aroxu.dawnshell.ui.LogDetailScreen

/** Full-screen, selectable, live-following view of one DawnShell log stream. */
class LogDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE)
        if (type == null || !DawnShellLogRepository.isKnown(type)) {
            DawnShellNotice.show(this, R.string.dawnshell_log_unknown)
            finish()
            return
        }
        setContent {
            DawnShellTheme {
                LogDetailScreen(
                    title = getString(DawnShellLogRepository.titleRes(type)),
                    description = getString(DawnShellLogRepository.descriptionRes(type)),
                    readLog = { readLog(type) },
                    onBack = ::finish,
                    onCopy = { value -> copyAll(type, value) },
                )
            }
        }
    }

    private fun readLog(type: String): String = try {
        DawnShellLogRepository.read(this, type)
    } catch (e: IOException) {
        getString(R.string.dawnshell_log_read_failed, BfuSu.sanitize(e.message))
    } catch (e: RuntimeException) {
        getString(R.string.dawnshell_log_read_failed, BfuSu.sanitize(e.message))
    }

    private fun copyAll(type: String, value: String) {
        if (value.isEmpty()) {
            DawnShellNotice.show(this, R.string.dawnshell_log_copy_empty)
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard == null) {
            DawnShellNotice.show(this, R.string.bfu_clipboard_unavailable)
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(DawnShellLogRepository.titleRes(type)), value))
        DawnShellNotice.show(this, R.string.dawnshell_log_copied)
    }

    companion object {
        private const val EXTRA_TYPE = "me.aroxu.dawnshell.extra.LOG_TYPE"

        @JvmStatic
        fun createIntent(context: Context, type: String): Intent =
            Intent(context, LogDetailActivity::class.java).putExtra(EXTRA_TYPE, type)
    }
}
