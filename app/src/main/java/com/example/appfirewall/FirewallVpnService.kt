package com.example.appfirewall

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class FirewallVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        stopVpn()

        val blocked = PrefsManager(this).getBlockedApps()
        if (blocked.isEmpty()) {
            stopSelf()
            return
        }

        val builder = Builder()
            .setSession("App Firewall")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)

        var added = 0
        for (pkg in blocked) {
            try {
                builder.addAllowedApplication(pkg)
                added++
            } catch (e: PackageManager.NameNotFoundException) {
                // اپ دیگر نصب نیست
            }
        }

        if (added == 0) {
            stopSelf()
            return
        }

        val pfd = builder.establish()
        if (pfd == null) {
            stopSelf()
            return
        }
        vpnInterface = pfd

        worker = Thread { answerPackets(pfd) }.apply {
            isDaemon = true
            start()
        }
    }

    // هر بسته‌ای از اپ مسدودشده بیاید، فوراً جواب «مقصد در دسترس نیست» داده می‌شود
    // تا اپ تلاش مجدد پشت‌سرهم نکند و گوشی گرم نشود.
    private fun answerPackets(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val len = input.read(buffer)
                if (len < 0) break
                if (len < 20) continue
                val reply = buildReply(buffer, len) ?: continue
                output.write(reply)
            }
        } catch (e: IOException) {
            // تونل بسته شده است
        }
    }

    private fun buildReply(p: ByteArray, len: Int): ByteArray? {
        if (((p[0].toInt() and 0xFF) shr 4) != 4) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (len < ihl + 8) return null
        return when (p[9].toInt() and 0xFF) {
            6 -> tcpReset(p, len, ihl)
            17 -> icmpUnreachable(p, len, ihl)
            else -> null
        }
    }

    private fun tcpReset(p: ByteArray, len: Int, ihl: Int): ByteArray? {
        if (len < ihl + 20) return null
        val flags = p[ihl + 13].toInt() and 0xFF
        if ((flags and 0x04) != 0) return null // به RST جواب نمی‌دهیم

        val dataOffset = ((p[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val totalLen = ((p[2].toInt() and 0xFF) shl 8) or (p[3].toInt() and 0xFF)
        val payloadLen = totalLen - ihl - dataOffset
        val seqOrig = readInt(p, ihl + 4)
        val ackOrig = readInt(p, ihl + 8)

        val tcp = ByteArray(20)
        tcp[0] = p[ihl + 2]
        tcp[1] = p[ihl + 3]
        tcp[2] = p[ihl]
        tcp[3] = p[ihl + 1]
        tcp[12] = 0x50.toByte()

        if ((flags and 0x10) != 0) {
            writeInt(tcp, 4, ackOrig)
            tcp[13] = 0x04.toByte()
        } else {
            val synFin = (if ((flags and 0x02) != 0) 1 else 0) + (if ((flags and 0x01) != 0) 1 else 0)
            writeInt(tcp, 8, seqOrig + payloadLen + synFin)
            tcp[13] = 0x14.toByte()
        }

        val ip = ipHeader(p, 40, 6)
        val pseudo = ByteArray(12)
        System.arraycopy(ip, 12, pseudo, 0, 8)
        pseudo[9] = 6
        pseudo[10] = 0
        pseudo[11] = 20
        val c = foldChecksum(sum16(pseudo, 0, 12, 0L) + sum16(tcp, 0, 20, 0L))
        tcp[16] = (c shr 8).toByte()
        tcp[17] = c.toByte()
        return ip + tcp
    }

    private fun icmpUnreachable(p: ByteArray, len: Int, ihl: Int): ByteArray {
        val n = minOf(len, ihl + 8)
        val icmp = ByteArray(8 + n)
        icmp[0] = 3
        icmp[1] = 3
        System.arraycopy(p, 0, icmp, 8, n)
        val c = foldChecksum(sum16(icmp, 0, icmp.size, 0L))
        icmp[2] = (c shr 8).toByte()
        icmp[3] = c.toByte()
        val ip = ipHeader(p, 20 + icmp.size, 1)
        return ip + icmp
    }

    // هدر IP پاسخ: مبدأ و مقصد جابه‌جا می‌شوند
    private fun ipHeader(p: ByteArray, total: Int, proto: Int): ByteArray {
        val h = ByteArray(20)
        h[0] = 0x45.toByte()
        h[2] = (total shr 8).toByte()
        h[3] = total.toByte()
        h[6] = 0x40.toByte()
        h[8] = 64
        h[9] = proto.toByte()
        System.arraycopy(p, 16, h, 12, 4)
        System.arraycopy(p, 12, h, 16, 4)
        val c = foldChecksum(sum16(h, 0, 20, 0L))
        h[10] = (c shr 8).toByte()
        h[11] = c.toByte()
        return h
    }

    private fun sum16(b: ByteArray, off: Int, len: Int, init: Long): Long {
        var s = init
        var i = off
        val end = off + len
        while (i < end) {
            val hi = (b[i].toInt() and 0xFF) shl 8
            val lo = if (i + 1 < end) (b[i + 1].toInt() and 0xFF) else 0
            s += (hi or lo).toLong()
            i += 2
        }
        return s
    }

    private fun foldChecksum(sum: Long): Int {
        var s = sum
        while ((s shr 16) != 0L) {
            s = (s and 0xFFFF) + (s shr 16)
        }
        return (s.inv() and 0xFFFF).toInt()
    }

    private fun readInt(b: ByteArray, off: Int): Int {
        return ((b[off].toInt() and 0xFF) shl 24) or
                ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or
                (b[off + 3].toInt() and 0xFF)
    }

    private fun writeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v shr 24).toByte()
        b[off + 1] = (v shr 16).toByte()
        b[off + 2] = (v shr 8).toByte()
        b[off + 3] = v.toByte()
    }

    private fun stopVpn() {
        worker?.interrupt()
        worker = null
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.appfirewall.STOP"

        fun start(context: Context) {
            context.startService(Intent(context, FirewallVpnService::class.java))
        }

        fun stop(context: Context) {
            val intent = Intent(context, FirewallVpnService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
