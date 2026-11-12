package com.example.appfirewall

import android.app.AppOpsManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process

class UsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val lastBytes = HashMap<String, Long>()
    private val foreground = HashSet<String>()
    private var lastEventTime = 0L
    private lateinit var usm: UsageStatsManager
    private lateinit var store: UsagePrefs

    private val tick = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        store = UsagePrefs(this)
        lastEventTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sample() {
        val now = System.currentTimeMillis()

        // وضعیت foreground هر اپ را از رویدادهای سیستم به‌روز می‌کنیم
        val events = usm.queryEvents(lastEventTime, now)
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> foreground.add(ev.packageName)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> foreground.remove(ev.packageName)
            }
        }
        lastEventTime = now

        // حجم داده هر UID از آخرین نمونه تا الان، اگر اپ در پس‌زمینه بوده، ثبت می‌شود
        val updates = HashMap<String, Long>()
        for (app in packageManager.getInstalledApplications(0)) {
            val rx = TrafficStats.getUidRxBytes(app.uid)
            val tx = TrafficStats.getUidTxBytes(app.uid)
            if (rx < 0 || tx < 0) continue

            val total = rx + tx
            val last = lastBytes.put(app.packageName, total)
            if (last == null || total < last) continue

            val delta = total - last
            if (delta > 0 && !foreground.contains(app.packageName)) {
                updates[app.packageName] = delta
            }
        }
        if (updates.isNotEmpty()) store.addAll(updates)
    }

    companion object {
        const val ACTION_STOP = "com.example.appfirewall.USAGE_STOP"
        private const val INTERVAL_MS = 15000L

        fun start(context: Context) {
            context.startService(Intent(context, UsageMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, UsageMonitorService::class.java).setAction(ACTION_STOP)
            )
        }

        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

class UsagePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("usage_prefs", Context.MODE_PRIVATE)

    fun addAll(deltas: Map<String, Long>) {
        val editor = prefs.edit()
        for ((pkg, delta) in deltas) {
            editor.putLong(pkg, prefs.getLong(pkg, 0L) + delta)
        }
        editor.apply()
    }

    fun getAll(): Map<String, Long> {
        return prefs.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
