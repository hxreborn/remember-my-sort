package eu.hxreborn.remembermysort.ui

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.edit
import eu.hxreborn.remembermysort.RememberMySortApp
import eu.hxreborn.remembermysort.prefs.PrefsManager
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class MainActivity :
    Activity(),
    XposedServiceHelper.OnServiceListener {
    private var remotePrefs: SharedPreferences? = null
    private var toggle: Switch? = null

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

        toggle =
            Switch(this).apply {
                text = "Per-folder sort preferences"
                isEnabled = false
                setOnCheckedChangeListener { _, isChecked ->
                    remotePrefs?.edit { putBoolean(PrefsManager.KEY_PER_FOLDER_ENABLED, isChecked) }
                }
            }

        layout.addView(title)
        layout.addView(description)
        layout.addView(experimentalNote)
        layout.addView(toggle)

        setContentView(layout)

        RememberMySortApp.addServiceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        RememberMySortApp.removeServiceListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        remotePrefs = service.getRemotePreferences(PrefsManager.PREFS_GROUP)
        runOnUiThread { loadPrefs() }
    }

    override fun onServiceDied(service: XposedService) {
        remotePrefs = null
        runOnUiThread {
            toggle?.isEnabled = false
        }
    }

    private fun loadPrefs() {
        val prefs = remotePrefs ?: return
        toggle?.apply {
            isEnabled = true
            isChecked = prefs.getBoolean(PrefsManager.KEY_PER_FOLDER_ENABLED, false)
        }
    }
}
