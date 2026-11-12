package com.example.appfirewall

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var adapter: AppAdapter
    private lateinit var editSearch: EditText
    private var allItems: List<AppItem> = emptyList()

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

        editSearch = findViewById(R.id.editSearch)
        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applySearch(s?.toString() ?: "")
            }
        })

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

            runOnUiThread {
                allItems = items
                applySearch(editSearch.text.toString())
            }
        }.start()
    }

    // فیلتر بر اساس نام یا نام پکیج؛ اگر جستجو خالی باشد، همه اپ‌ها نمایش داده می‌شوند
    private fun applySearch(query: String) {
        val q = query.trim().toLowerCase()
        val filtered = if (q.isEmpty()) {
            allItems
        } else {
            allItems.filter {
                it.label.toLowerCase().contains(q) || it.packageName.toLowerCase().contains(q)
            }
        }
        adapter.setItems(filtered)
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
