package com.example.appfirewall

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        adapter = AppAdapter(mutableListOf()) { packageName, blocked ->
            onToggle(packageName, blocked)
        }

        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            prefs.setBlockedApps(emptySet())
            FirewallVpnService.stop(this)
            loadApps()
            Toast.makeText(this, "فایروال متوقف شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnUsage).setOnClickListener {
            startActivity(Intent(this, UsageActivity::class.java))
        }

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val blocked = prefs.getBlockedApps()

            val items = pm.getInstalledApplications(0)
                .filter { it.packageName != packageName }
                .map { info ->
                    AppItem(
                        label = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName,
                        icon = pm.getApplicationIcon(info),
                        blocked = blocked.contains(info.packageName)
                    )
                }
                .sortedBy { it.label.toLowerCase() }

            runOnUiThread { adapter.setItems(items) }
        }.start()
    }

    private fun onToggle(packageName: String, blocked: Boolean) {
        val set = prefs.getBlockedApps().toMutableSet()
        if (blocked) set.add(packageName) else set.remove(packageName)
        prefs.setBlockedApps(set)
        applyChanges()
    }

    private fun applyChanges() {
        if (prefs.getBlockedApps().isEmpty()) {
            FirewallVpnService.stop(this)
            return
        }

        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, REQ_VPN)
        } else {
            FirewallVpnService.start(this)
        }
    }

    @Deprecated("Deprecated in newer APIs, kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            if (resultCode == Activity.RESULT_OK) {
                FirewallVpnService.start(this)
            } else {
                Toast.makeText(this, R.string.vpn_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQ_VPN = 1
    }
}
