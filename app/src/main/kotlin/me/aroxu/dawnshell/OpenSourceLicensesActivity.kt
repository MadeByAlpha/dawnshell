package me.aroxu.dawnshell

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import me.aroxu.dawnshell.ui.DawnShellTheme
import me.aroxu.dawnshell.ui.OpenSourceLicensesScreen

/** Selectable copy of every license and source offer shipped inside the APK. */
class OpenSourceLicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = try {
            readBundle(assets)
        } catch (e: IOException) {
            getString(R.string.dawnshell_licenses_read_failed, e.message)
        }
        setContent {
            DawnShellTheme {
                OpenSourceLicensesScreen(text, onBack = ::finish, onCopy = { copyAll(text) })
            }
        }
    }

    private fun readBundle(assets: AssetManager): String = DOCUMENTS.joinToString("\n\n") { document ->
        val lines = assets.open(ASSET_ROOT + document).bufferedReader(Charsets.UTF_8).use { it.readLines() }
        "===== $document =====\n\n" + lines.joinToString("") { "$it\n" }
    }

    private fun copyAll(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (clipboard == null) {
            DawnShellNotice.show(this, R.string.bfu_clipboard_unavailable)
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dawnshell_open_source_licenses), text))
        DawnShellNotice.show(this, R.string.dawnshell_licenses_copied)
    }

    private companion object {
        const val ASSET_ROOT = "open_source_licenses/"
        val DOCUMENTS = listOf(
            "SOURCE_OFFER.txt",
            "README.md",
            "THIRD_PARTY_NOTICES.md",
            "ANDROID_DEPENDENCIES.md",
            "DawnShell-MIT.txt",
            "Apache-2.0.txt",
            "CC0-1.0.txt",
            "Expat.txt",
            "GPL-2.0-only.txt",
            "GPL-2.0-or-later.txt",
            "GPL-3.0-or-later.txt",
            "LGPL-2.1-or-later.txt",
            "LGPL-3.0-or-later.txt",
            "base-installer-1.226-copyright.txt",
            "debian-archive-keyring-2025.1-copyright.txt",
            "GnuPG-2.4.9-additional-notices.txt",
            "Libgcrypt-1.12.1-additional-notices.txt",
        )
    }
}
