package com.example.appfirewall

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UsageActivity : AppCompatActivity() {

    private lateinit var store: UsagePrefs
    private lateinit var list: ListView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage)

        store = UsagePrefs(this)
        list = findViewById(R.id.listUsage)
        status = findViewById(R.id.txtStatus)

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (UsageMonitorService.hasUsageAccess(this)) {
                UsageMonitorService.start(this)
                status.text = "پایش شروع شد. هر ۱۵ ثانیه نمونه‌برداری می‌شود."
            } else {
                status.text = "اول دسترسی Usage access را بدهید."
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            UsageMonitorService.stop(this)
            status.text = "پایش متوقف شد."
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            store.clear()
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val pm = packageManager
        val rows = store.getAll().toList()
            .sortedByDescending { it.second }
            .map { (pkg, bytes) ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    pkg
                }
                "$label\n${formatBytes(bytes)}"
            }

        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            rows.ifEmpty { listOf("هنوز داده‌ای ثبت نشده") }
        )

        if (!UsageMonitorService.hasUsageAccess(this)) {
            status.text = "دسترسی Usage access داده نشده است."
        }
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1024L * 1024 * 1024 -> String.format("%.2f GB", b / 1073741824.0)
        b >= 1024L * 1024 -> String.format("%.2f MB", b / 1048576.0)
        b >= 1024L -> String.format("%.1f KB", b / 1024.0)
        else -> "$b B"
    }
}
