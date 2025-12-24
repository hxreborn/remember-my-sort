package eu.hxreborn.remembermysort.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import eu.hxreborn.remembermysort.prefs.AppPrefsHelper

/**
 * Simple settings activity for toggling per-folder sort preferences.
 * This is an experimental feature - when disabled, stock global behavior is used.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
            }

        val title =
            TextView(this).apply {
                text = "Remember My Sort"
                textSize = 24f
                setPadding(0, 0, 0, 32)
            }

        val description =
            TextView(this).apply {
                text =
                    "When enabled, each folder remembers its own sort preference. " +
                        "When disabled, all folders share one global sort preference (stock behavior)."
                setPadding(0, 0, 0, 32)
            }

        val experimentalNote =
            TextView(this).apply {
                text = "This is an experimental feature. Restart DocumentsUI after changing."
                textSize = 12f
                setPadding(0, 0, 0, 24)
            }

        val toggle =
            Switch(this).apply {
                text = "Per-folder sort preferences"
                isChecked = AppPrefsHelper.isPerFolderEnabled(this@MainActivity)
                setOnCheckedChangeListener { _, isChecked ->
                    AppPrefsHelper.setPerFolderEnabled(this@MainActivity, isChecked)
                }
            }

        layout.addView(title)
        layout.addView(description)
        layout.addView(experimentalNote)
        layout.addView(toggle)

        setContentView(layout)
    }
}
