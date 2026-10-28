package com.vstretch.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.FileDescriptor
import java.io.FileInputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * SỬA CẢM ỨNG khi "kéo giãn thật" (giống "Modo Pro" của Cat Resolution).
 *
 * Vấn đề: Sf.kt kéo HÌNH ra toàn panel, nhưng hệ thống vẫn ánh xạ CHẠM theo vùng vừa khít cũ
 * (có viền) -> dải viền cũ không nhận chạm, vùng gần nó bị lệch. Không thể sửa ánh xạ đó từ shell.
 *
 * Cách làm (chạy trong tiến trình shell uid 2000 do Shizuku tạo):
 *  1. Đọc thẳng sự kiện thô từ /dev/input/eventX của màn hình cảm ứng (đa điểm, giao thức B).
 *  2. EVIOCGRAB để hệ thống KHÔNG nhận chạm gốc nữa (tránh chạm đúp).
 *  3. Đổi toạ độ thô -> toạ độ màn hình logic đúng với hình đã kéo giãn (có tính xoay màn hình)
 *     rồi chèn lại bằng InputManager.injectInputEvent (cùng cách scrcpy dùng).
 *
 * AN TOÀN (vì giữ thiết bị chạm có thể làm kẹt cảm ứng nếu lỗi):
 *  - Chỉ giữ thiết bị sau khi đã tìm được đường chèn sự kiện.
 *  - Chèn thất bại 5 lần liên tiếp -> tự nhả thiết bị (cảm ứng trở lại như cũ).
 *  - Bấm Volume+ 3 lần trong 2.5 giây -> nhả cảm ứng + trả `wm size/density` + hiển thị về mặc định.
 *  - Tiến trình shell chết (đóng app / dừng Shizuku) -> kernel tự nhả (đóng fd).
 */
object Touch {
    private const val MAXS = 16
    private const val EVIOCGRAB = 0x40044590          // _IOW('E', 0x90, int)
    private const val EMERGENCY_PRESSES = 3

    private class Cfg(
        val lnW: Int, val lnH: Int, val pw: Int, val ph: Int,
        val baseW: Int, val baseH: Int, val baseDpi: Int,
        val baseSizeOverride: Boolean, val baseDensityOverride: Boolean
    )
    private class TDev(val path: String, val name: String, val minX: Int, val maxX: Int, val minY: Int, val maxY: Int)
    private class Mt {
        val x = IntArray(MAXS); val y = IntArray(MAXS)
        val act = BooleanArray(MAXS); val prev = BooleanArray(MAXS)
        var cur = 0; var moved = false; var downTime = 0L
    }

    private val lock = Any()
    @Volatile private var cfg: Cfg? = null
    @Volatile private var running = false
    @Volatile private var rot = 0
    private var touchIn: FileInputStream? = null
    private val keyIns = ArrayList<FileInputStream>()
    private val threads = ArrayList<Thread>()
    private val keyPaths = ArrayList<String>()
    private var tdev: TDev? = null
    private var devId = 0
    private var injTarget: Any? = null
    private var injMethod: Method? = null
    private var injHow = "chưa khởi tạo"
    private var info = "chưa chạy"
    private var err = ""
    private var injected = 0L
    @Volatile private var failStreak = 0

    // ------------------------------------------------------------------ API cho ShellService

    /** on=true: bật/cập nhật ánh xạ. on=false: nhả thiết bị chạm. kích thước theo hướng tự nhiên của máy. */
    fun set(
        on: Boolean, lnW: Int, lnH: Int, pw: Int, ph: Int,
        baseW: Int, baseH: Int, baseDpi: Int,
        baseSizeOverride: Boolean, baseDensityOverride: Boolean
    ): String = synchronized(lock) {
        if (!on) {
            if (running) stopLocked("tắt theo yêu cầu")
            return@synchronized "OK: sửa cảm ứng đang tắt"
        }
        if (lnW <= 0 || lnH <= 0 || pw <= 0 || ph <= 0) return@synchronized "ERR: kích thước không hợp lệ"
        cfg = Cfg(lnW, lnH, pw, ph, baseW, baseH, baseDpi, baseSizeOverride, baseDensityOverride)
        if (running) return@synchronized "OK: đã cập nhật ánh xạ cảm ứng ($info)"
        startLocked()
    }

    fun diag(): String = synchronized(lock) {
        val c = cfg
        val d = tdev
        buildString {
            appendLine("Trạng thái: ${if (running) "ĐANG CHẠY" else "tắt"} — $info")
            appendLine("Thiết bị chạm: ${if (d == null) "—" else "${d.path} “${d.name}” X[${d.minX}..${d.maxX}] Y[${d.minY}..${d.maxY}]"} (deviceId=$devId)")
            appendLine("Thiết bị Volume: ${if (keyPaths.isEmpty()) "không thấy" else keyPaths.joinToString()}")
            appendLine("Chèn sự kiện: $injHow")
            appendLine("Đã chèn: $injected · lỗi liên tiếp: $failStreak · xoay: $rot")
            appendLine("Cấu hình: ${if (c == null) "—" else "logic ${c.lnW}×${c.lnH}, panel ${c.pw}×${c.ph}"}")
            if (err.isNotEmpty()) append("Lỗi gần nhất: $err")
        }
    }

    // ------------------------------------------------------------------ khởi động / dừng

    private fun startLocked(): String {
        err = ""
        val d = findDevices() ?: return "ERR: $err"
        val ie = initInjector()
        if (ie != null) return "ERR: không có đường chèn sự kiện chạm ($ie)"
        val fis = try { FileInputStream(d.path) } catch (t: Throwable) { return "ERR: không mở được ${d.path}: ${root(t)}" }
        try { grab(fis.fd) } catch (t: Throwable) {
            try { fis.close() } catch (_: Throwable) {}
            return "ERR: ROM không cho giữ thiết bị chạm (EVIOCGRAB): ${root(t)}"
        }
        tdev = d; devId = findDeviceId(d.name); touchIn = fis
        injected = 0; failStreak = 0; running = true
        info = "đang chạy trên ${d.path} (${d.name})"
        threads.clear()
        threads += spawn("sx-touch") { readLoop(fis, d) }
        threads += spawn("sx-rot") { rotLoop() }
        for (p in keyPaths) threads += spawn("sx-key") { keyLoop(p) }
        return "OK: $info; bấm Volume+ $EMERGENCY_PRESSES lần để khôi phục khẩn cấp"
    }

    private fun stopLocked(why: String) {
        running = false
        try { touchIn?.close() } catch (_: Throwable) {}   // đóng fd = nhả EVIOCGRAB
        touchIn = null
        for (k in keyIns) try { k.close() } catch (_: Throwable) {}
        keyIns.clear()
        for (t in threads) t.interrupt()
        threads.clear()
        cfg = null
        info = "đã dừng: $why"
    }

    private fun spawn(name: String, f: () -> Unit): Thread = Thread({ f() }, name).apply { isDaemon = true; start() }

    private fun root(t: Throwable): String {
        var e: Throwable = t
        while (e is InvocationTargetException && e.cause != null) e = e.cause!!
        return "${e.javaClass.simpleName}: ${e.message}"
    }

    // ------------------------------------------------------------------ tìm thiết bị / ioctl / chèn sự kiện

    private fun findDevices(): TDev? {
        val txt = try {
            val p = ProcessBuilder("getevent", "-lp").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().readText().also { p.waitFor() }
        } catch (t: Throwable) { err = "getevent: ${root(t)}"; return null }

        keyPaths.clear()
        var best: TDev? = null
        var bestDirect = false
        val rxX = Regex("ABS_MT_POSITION_X\\s*:\\s*value\\s*-?\\d+,\\s*min\\s*(-?\\d+),\\s*max\\s*(-?\\d+)")
        val rxY = Regex("ABS_MT_POSITION_Y\\s*:\\s*value\\s*-?\\d+,\\s*min\\s*(-?\\d+),\\s*max\\s*(-?\\d+)")
        for (b in txt.split(Regex("(?m)^add device \\d+:")).drop(1)) {
            val path = Regex("^\\s*(/dev/input/event\\d+)").find(b)?.groupValues?.get(1) ?: continue
            val name = Regex("name:\\s*\"(.*)\"").find(b)?.groupValues?.get(1) ?: ""
            if ("KEY_VOLUMEUP" in b) keyPaths.add(path)
            val mx = rxX.find(b) ?: continue
            val my = rxY.find(b) ?: continue
            if ("ABS_MT_SLOT" !in b) continue                 // chỉ hỗ trợ giao thức đa điểm B
            val direct = "INPUT_PROP_DIRECT" in b
            if (best == null || (direct && !bestDirect)) {
                best = TDev(path, name, mx.groupValues[1].toInt(), mx.groupValues[2].toInt(), my.groupValues[1].toInt(), my.groupValues[2].toInt())
                bestDirect = direct
            }
        }
        if (best == null) err = "không thấy màn hình cảm ứng (giao thức đa điểm B) trong `getevent -lp`"
        return best
    }

    /** EVIOCGRAB qua libcore.io.Os.ioctlInt (truyền con trỏ khác null = "giữ"). Nhả bằng cách đóng fd. */
    private fun grab(fd: FileDescriptor) {
        val os = Class.forName("libcore.io.Libcore").getField("os").get(null)!!
        val m = Class.forName("libcore.io.Os").getMethod("ioctlInt", FileDescriptor::class.java, Int::class.javaPrimitiveType!!)
        try { m.invoke(os, fd, EVIOCGRAB) } catch (e: InvocationTargetException) { throw e.cause ?: e }
    }

    private fun initInjector(): String? {
        if (injMethod != null) return null
        val errs = ArrayList<String>()
        for (n in listOf("android.hardware.input.InputManagerGlobal", "android.hardware.input.InputManager")) {
            try {
                val c = Class.forName(n)
                val inst = c.getDeclaredMethod("getInstance").apply { isAccessible = true }.invoke(null)
                    ?: throw IllegalStateException("getInstance=null")
                val m = c.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType!!)
                injTarget = inst; injMethod = m; injHow = "$n.injectInputEvent"
                return null
            } catch (t: Throwable) { errs += "$n:${root(t)}" }
        }
        return errs.joinToString(" | ")
    }

    private fun findDeviceId(name: String): Int = try {
        var found = 0
        for (id in InputDevice.getDeviceIds()) { if (InputDevice.getDevice(id)?.name == name) { found = id; break } }
        found
    } catch (_: Throwable) { 0 }

    // ------------------------------------------------------------------ luồng đọc cảm ứng

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun i32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    private fun readLoop(fis: FileInputStream, d: TDev) {
        val is64 = android.os.Process.is64Bit()
        val sz = if (is64) 24 else 16       // struct input_event
        val to = if (is64) 16 else 8        // offset của type
        val buf = ByteArray(sz * 64)
        val m = Mt()
        try {
            while (running) {
                val len = fis.read(buf)
                if (len <= 0) break
                var o = 0
                while (o + sz <= len) {
                    val type = u16(buf, o + to); val code = u16(buf, o + to + 2); val v = i32(buf, o + to + 4)
                    o += sz
                    if (type == 3) {                                   // EV_ABS
                        when (code) {
                            0x2f -> m.cur = v.coerceIn(0, MAXS - 1)    // ABS_MT_SLOT
                            0x39 -> m.act[m.cur] = v >= 0              // ABS_MT_TRACKING_ID
                            0x35 -> { m.x[m.cur] = v; m.moved = true } // ABS_MT_POSITION_X
                            0x36 -> { m.y[m.cur] = v; m.moved = true } // ABS_MT_POSITION_Y
                        }
                    } else if (type == 0 && code == 0) {               // SYN_REPORT
                        if (!flush(m, d)) {
                            synchronized(lock) { if (running) stopLocked("chèn sự kiện thất bại liên tiếp — đã trả cảm ứng về như cũ ($err)") }
                            return
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            if (running) err = "đọc cảm ứng: ${root(t)}"
        } finally {
            synchronized(lock) { if (touchIn === fis && running) stopLocked("luồng đọc dừng${if (err.isEmpty()) "" else ": $err"}") }
        }
    }

    /** Phát các sự kiện (MOVE / DOWN / POINTER_DOWN / POINTER_UP / UP) cho khung vừa đọc. */
    private fun flush(m: Mt, d: TDev): Boolean {
        val c = cfg ?: return false
        val now = SystemClock.uptimeMillis()
        val list = ArrayList<Int>()
        for (s in 0 until MAXS) if (m.prev[s]) list.add(s)
        if (m.moved && list.isNotEmpty()) send(MotionEvent.ACTION_MOVE, list, m, d, c, now)
        for (s in 0 until MAXS) if (m.act[s] && !m.prev[s]) {
            if (list.isEmpty()) m.downTime = now
            list.add(s); list.sort()
            val idx = list.indexOf(s)
            val a = if (list.size == 1) MotionEvent.ACTION_DOWN
                    else MotionEvent.ACTION_POINTER_DOWN or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            send(a, list, m, d, c, now)
        }
        for (s in 0 until MAXS) if (!m.act[s] && m.prev[s]) {
            val idx = list.indexOf(s)
            val a = if (list.size == 1) MotionEvent.ACTION_UP
                    else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            send(a, list, m, d, c, now)
            list.remove(s)
        }
        for (s in 0 until MAXS) m.prev[s] = m.act[s]
        m.moved = false
        return failStreak < 5
    }

    private fun send(action: Int, list: List<Int>, m: Mt, d: TDev, c: Cfg, now: Long): Boolean {
        val n = list.size
        val r = rot
        val odd = r % 2 == 1
        val cw = if (odd) c.ph else c.pw; val ch = if (odd) c.pw else c.ph       // panel theo hướng hiện tại
        val lw = if (odd) c.lnH else c.lnW; val lh = if (odd) c.lnW else c.lnH   // màn hình logic theo hướng hiện tại
        val rx = (d.maxX - d.minX).coerceAtLeast(1).toFloat()
        val ry = (d.maxY - d.minY).coerceAtLeast(1).toFloat()
        val props = Array(n) { i -> MotionEvent.PointerProperties().apply { id = list[i]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coords = Array(n) { i ->
            val s = list[i]
            val nx = (m.x[s] - d.minX) / rx * c.pw        // px tự nhiên của panel
            val ny = (m.y[s] - d.minY) / ry * c.ph
            val px: Float; val py: Float
            when (r) {                                    // quay về hướng đang hiển thị
                1 -> { px = ny; py = c.pw - nx }
                2 -> { px = c.pw - nx; py = c.ph - ny }
                3 -> { px = c.ph - ny; py = nx }
                else -> { px = nx; py = ny }
            }
            MotionEvent.PointerCoords().apply {           // hình đã kéo giãn: panel đầy đủ <-> màn hình logic đầy đủ
                x = (px * lw / cw).coerceIn(0f, lw - 1f)
                y = (py * lh / ch).coerceIn(0f, lh - 1f)
                pressure = 1f; size = 0.1f
            }
        }
        val ev = MotionEvent.obtain(m.downTime, now, action, n, props, coords, 0, 0, 1f, 1f, devId, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        var ok = false
        try { ok = (injMethod!!.invoke(injTarget, ev, 0) as? Boolean) == true }   // 0 = INJECT_INPUT_EVENT_MODE_ASYNC
        catch (t: Throwable) { err = "inject: ${root(t)}" }
        finally { ev.recycle() }
        if (ok) { injected++; failStreak = 0 } else failStreak++
        return ok
    }

    private fun rotLoop() {
        while (running) {
            try { rot = Sf.rotation() and 3 } catch (_: Throwable) {}
            try { Thread.sleep(200) } catch (_: InterruptedException) { return }
        }
    }

    // ------------------------------------------------------------------ khôi phục khẩn cấp (Volume+)

    private fun keyLoop(path: String) {
        val fis = try { FileInputStream(path) } catch (_: Throwable) { return }
        synchronized(lock) {
            if (!running) { try { fis.close() } catch (_: Throwable) {}; return }
            keyIns.add(fis)
        }
        val is64 = android.os.Process.is64Bit()
        val sz = if (is64) 24 else 16
        val to = if (is64) 16 else 8
        val buf = ByteArray(sz * 32)
        var cnt = 0
        var last = 0L
        try {
            while (running) {
                val len = fis.read(buf)
                if (len <= 0) break
                var o = 0
                while (o + sz <= len) {
                    val type = u16(buf, o + to); val code = u16(buf, o + to + 2); val v = i32(buf, o + to + 4)
                    o += sz
                    if (type == 1 && code == 115 && v == 1) {          // EV_KEY, KEY_VOLUMEUP, nhấn xuống
                        val t = SystemClock.uptimeMillis()
                        if (t - last > 2500) cnt = 0
                        last = t; cnt++
                        if (cnt >= EMERGENCY_PRESSES) { emergency("Volume+ ×$EMERGENCY_PRESSES"); return }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun emergency(why: String) {
        val c = cfg
        synchronized(lock) { if (running) stopLocked("KHẨN CẤP: $why") }
        try { if (c != null) Sf.set(c.lnW, c.lnH, c.pw, c.ph, false) } catch (_: Throwable) {}
        val size = if (c != null && c.baseSizeOverride && c.baseW > 0 && c.baseH > 0) "wm size ${c.baseW}x${c.baseH}" else "wm size reset"
        val density = if (c != null && c.baseDensityOverride && c.baseDpi > 0) "wm density ${c.baseDpi}" else "wm density reset"
        try { ProcessBuilder("sh", "-c", "$size; $density").redirectErrorStream(true).start().waitFor() } catch (_: Throwable) {}
        info = "KHẨN CẤP ($why): đã nhả cảm ứng và khôi phục wm size/density ban đầu"
    }
}
