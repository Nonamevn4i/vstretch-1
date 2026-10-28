package com.vstretch.app

import android.os.SystemClock
import java.io.FileInputStream

/**
 * Nghe NHẤN ĐÚP phím Volume − (chạy trong tiến trình shell của Shizuku) để ẩn/hiện bong bóng nổi khi đang chơi.
 * Chỉ ĐỌC /dev/input/eventX (không EVIOCGRAB) nên phím Volume vẫn hoạt động bình thường với hệ thống.
 * App đăng ký một callback binder (IKeyCb); nếu app chết thì lần gọi sau lỗi -> tự ngừng nghe.
 */
object Keys {
    private const val KEY_VOLUMEDOWN = 114
    private const val DOUBLE_MS = 650L

    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var gen = 0
    private var cb: IKeyCb? = null
    private val ins = ArrayList<FileInputStream>()

    fun set(c: IKeyCb?): String = synchronized(lock) {
        stopLocked()
        if (c == null) return@synchronized "OK: đã tắt phím Volume −"
        val ps = findPaths()
        if (ps.isEmpty()) return@synchronized "ERR: không thấy thiết bị phím Volume"
        cb = c
        running = true
        val g = gen
        for (p in ps) {
            val t = Thread({ loop(p, g) }, "sx-vol")
            t.isDaemon = true
            t.start()
        }
        "OK: đang nghe Volume − trên ${ps.joinToString()}"
    }

    /** Phải gọi khi đang giữ [lock]. */
    private fun stopLocked() {
        running = false
        gen++
        for (f in ins) { try { f.close() } catch (_: Throwable) {} }
        ins.clear()
        cb = null
    }

    private fun findPaths(): List<String> {
        val txt = try {
            val p = ProcessBuilder("getevent", "-lp").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText().also { p.waitFor() }
        } catch (_: Throwable) { return emptyList() }
        val out = ArrayList<String>()
        for (b in txt.split(Regex("(?m)^add device \\d+:")).drop(1)) {
            val path = Regex("^\\s*(/dev/input/event\\d+)").find(b)?.groupValues?.get(1) ?: continue
            if ("KEY_VOLUMEDOWN" in b) out.add(path)
        }
        return out
    }

    private fun loop(path: String, g: Int) {
        val fis = try { FileInputStream(path) } catch (_: Throwable) { return }
        synchronized(lock) {
            if (!running || g != gen) { try { fis.close() } catch (_: Throwable) {}; return }
            ins.add(fis)
        }
        val is64 = android.os.Process.is64Bit()
        val sz = if (is64) 24 else 16
        val to = if (is64) 16 else 8
        val buf = ByteArray(sz * 32)
        var last = 0L
        try {
            while (running && g == gen) {
                val len = fis.read(buf)
                if (len <= 0) break
                var o = 0
                while (o + sz <= len) {
                    val type = u16(buf, o + to)
                    val code = u16(buf, o + to + 2)
                    val v = i32(buf, o + to + 4)
                    o += sz
                    if (type == 1 && code == KEY_VOLUMEDOWN && v == 1) {      // EV_KEY, nhấn xuống
                        val t = SystemClock.uptimeMillis()
                        if (last != 0L && t - last <= DOUBLE_MS) { last = 0L; fire(g) } else last = t
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun fire(g: Int) {
        val c = cb ?: return
        try {
            c.onKey(KEY_VOLUMEDOWN)
        } catch (_: Throwable) {
            synchronized(lock) { if (g == gen) stopLocked() }     // app đã chết -> thôi nghe
        }
    }

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun i32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
