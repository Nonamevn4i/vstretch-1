package com.vstretch.app

import android.graphics.Rect
import android.os.IBinder
import java.lang.reflect.InvocationTargetException

/**
 * KÉO GIÃN THẬT ở tầng compositor (SurfaceFlinger).
 *
 * CHẠY TRONG TIẾN TRÌNH SHELL (uid 2000) do Shizuku tạo -> shell có quyền ACCESS_SURFACE_FLINGER
 * (cùng cơ chế scrcpy dùng). Không bao giờ gọi từ tiến trình app thường.
 *
 * Vì sao cần: `wm size` chỉ đổi kích thước "màn hình logic". DisplayManager luôn đặt vùng hiển thị
 * (displayRect) theo kiểu *vừa khít, giữ tỉ lệ* => phần dư thành viền đen (video 2: 2880x1606 trong 2880x1800).
 * Ở đây ta gửi thẳng cho SurfaceFlinger phép chiếu mới: layerStackRect (toàn bộ màn hình logic)
 * -> displayRect (TOÀN BỘ panel). Kết quả: hình bị kéo giãn lấp đầy, không còn viền.
 * Game không hề biết (nó vẫn thấy màn hình logic 16:9) nên không bị khóa tỉ lệ.
 *
 * DisplayManager chỉ gửi lại phép chiếu khi trạng thái của nó đổi (xoay màn hình, đổi `wm size`...),
 * nên có một luồng canh gác (watchdog) áp dụng lại khi cần và TỰ DỪNG nếu kích thước logic không còn khớp.
 */
object Sf {
    private class Cfg(val lnW: Int, val lnH: Int, val pw: Int, val ph: Int)

    private val lock = Any()
    private var cfg: Cfg? = null
    private var watch: Thread? = null
    @Volatile private var watchRun = false

    private var token: IBinder? = null
    private var tokenHow = "chưa lấy"
    private var applyHow = "chưa áp dụng"
    private var lastInfo = "chưa áp dụng lần nào"
    private var lastErr = ""
    private var applyCount = 0

    private val INT = Int::class.javaPrimitiveType!!
    private val LONG = Long::class.javaPrimitiveType!!

    // ------------------------------------------------------------------ API cho ShellService

    /** fill=true: kéo giãn lấp đầy panel và canh gác. fill=false: trả về phép chiếu mặc định (giữ tỉ lệ) rồi dừng canh gác. */
    fun set(lnW: Int, lnH: Int, pw: Int, ph: Int, fill: Boolean): String {
        if (lnW <= 0 || lnH <= 0 || pw <= 0 || ph <= 0) return "ERR: kích thước không hợp lệ ($lnW×$lnH / $pw×$ph)"
        val c = Cfg(lnW, lnH, pw, ph)
        if (!fill) {
            synchronized(lock) { stopWatch(); cfg = null }
            val r = synchronized(lock) { applyNow(c, fill = false, force = true) }
            return if (r.startsWith("ERR")) r else "OK: đã trả về hiển thị mặc định ($r)"
        }
        // `wm size` vừa chạy xong có thể chưa kịp có hiệu lực -> chờ tối đa ~2 giây cho kích thước logic khớp
        var r = "ERR: chưa thử"
        for (i in 0 until 9) {
            r = synchronized(lock) { cfg = c; applyNow(c, fill = true, force = true) }
            if (!r.startsWith("WAIT")) break
            try { Thread.sleep(250) } catch (_: InterruptedException) { break }
        }
        if (r.startsWith("WAIT")) { synchronized(lock) { cfg = null }; return "ERR: " + r.removePrefix("WAIT: ") }
        if (r.startsWith("ERR")) { synchronized(lock) { cfg = null }; return r }
        synchronized(lock) { startWatch() }
        return r
    }

    fun diag(): String = synchronized(lock) {
        val c = cfg
        buildString {
            appendLine("Canh gác: ${if (watchRun) "đang chạy" else "tắt"}")
            appendLine("Cấu hình: ${if (c == null) "—" else "logic ${c.lnW}×${c.lnH}, panel ${c.pw}×${c.ph}"}")
            appendLine("Token màn hình: $tokenHow")
            appendLine("Cách gửi lệnh SF: $applyHow")
            appendLine("Số lần áp dụng: $applyCount")
            appendLine("Lần cuối: $lastInfo")
            if (lastErr.isNotEmpty()) appendLine("Lỗi gần nhất: $lastErr")
            append("Android API ${android.os.Build.VERSION.SDK_INT}")
        }
    }

    // ------------------------------------------------------------------ lõi

    private data class Disp(val rot: Int, val lw: Int, val lh: Int)

    /** Đọc xoay + kích thước logic hiện tại của màn hình chính (qua DisplayManagerGlobal; chạy được ở tiến trình shell). */
    private fun readDisplay(): Disp? = try {
        val g = Class.forName("android.hardware.display.DisplayManagerGlobal").getMethod("getInstance").invoke(null)!!
        val info = g.javaClass.getMethod("getDisplayInfo", INT).invoke(g, 0)!!
        val cls = info.javaClass
        Disp(cls.getField("rotation").getInt(info), cls.getField("logicalWidth").getInt(info), cls.getField("logicalHeight").getInt(info))
    } catch (t: Throwable) {
        lastErr = "đọc DisplayInfo: ${root(t)}"; null
    }

    /** Phép chiếu mà DisplayManager vẫn dùng: vừa khít, giữ tỉ lệ, căn giữa. */
    private fun fit(lw: Int, lh: Int, pw: Int, ph: Int): Rect {
        val dw: Int; val dh: Int
        if (pw.toLong() * lh < ph.toLong() * lw) { dw = pw; dh = (lh.toLong() * pw / lw).toInt() }
        else { dw = (lw.toLong() * ph / lh).toInt(); dh = ph }
        val l = (pw - dw) / 2; val t = (ph - dh) / 2
        return Rect(l, t, l + dw, t + dh)
    }

    /** @return "OK: ..." hoặc "ERR: ..." */
    private fun applyNow(c: Cfg, fill: Boolean, force: Boolean): String {
        val d = readDisplay() ?: return "ERR: không đọc được trạng thái màn hình ($lastErr)"
        val rot = d.rot and 3
        val swap = rot % 2 == 1
        val expLw = if (swap) c.lnH else c.lnW
        val expLh = if (swap) c.lnW else c.lnH
        val mismatch = d.lw != expLw || d.lh != expLh
        if (fill && mismatch) {
            return if (force) "WAIT: kích thước logic hiện tại ${d.lw}×${d.lh} khác dự kiến $expLw×$expLh (`wm size` chưa có hiệu lực?)"
            else "STALE"
        }
        val pw = if (swap) c.ph else c.pw
        val ph = if (swap) c.pw else c.ph
        val stack = Rect(0, 0, d.lw, d.lh)
        val disp = if (fill) Rect(0, 0, pw, ph) else fit(d.lw, d.lh, pw, ph)

        val tk = displayToken() ?: return "ERR: không lấy được token màn hình ($tokenHow)"
        val how = setProjection(tk, rot, stack, disp) ?: return "ERR: SurfaceFlinger từ chối ($lastErr)"
        applyCount++
        lastInfo = "xoay=$rot, layerStack=${stack.toShortString()}, displayRect=${disp.toShortString()} qua $how"
        return "OK: $lastInfo"
    }

    // ------------------------------------------------------------------ canh gác

    private fun startWatch() {
        if (watchRun) return
        watchRun = true
        watch = Thread({
            var lastRot = -1
            var lastApply = 0L
            while (watchRun) {
                try {
                    Thread.sleep(350)
                    val c = synchronized(lock) { cfg } ?: break
                    val d = readDisplay() ?: continue
                    val now = System.currentTimeMillis()
                    if (d.rot != lastRot || now - lastApply > 1500) {
                        val r = synchronized(lock) { if (cfg === c) applyNow(c, fill = true, force = false) else "SKIP" }
                        if (r == "STALE") {
                            // kích thước logic đã bị đổi từ nơi khác (adb / app khác): dừng, KHÔNG ép phép chiếu cũ
                            synchronized(lock) {
                                lastInfo = "tự dừng: kích thước logic đã đổi (${d.lw}×${d.lh}) — trả về mặc định"
                                cfg = null
                                watchRun = false
                            }
                            break
                        }
                        lastRot = d.rot; lastApply = now
                    }
                } catch (_: InterruptedException) { break } catch (t: Throwable) { lastErr = "canh gác: ${root(t)}" }
            }
            watchRun = false
        }, "stretchx-watch").apply { isDaemon = true; start() }
    }

    private fun stopWatch() {
        watchRun = false
        watch?.interrupt()
        watch = null
    }

    // ------------------------------------------------------------------ SurfaceControl qua reflection

    private val sc: Class<*> by lazy { Class.forName("android.view.SurfaceControl") }

    private fun root(t: Throwable): String {
        var e: Throwable = t
        while (e is InvocationTargetException && e.cause != null) e = e.cause!!
        return "${e.javaClass.simpleName}: ${e.message}"
    }

    /** Lấy token của panel chính, thử lần lượt các cách theo đời Android (10 → 16). */
    private fun displayToken(): IBinder? {
        token?.let { return it }
        val errs = ArrayList<String>()

        fun attempt(name: String, f: () -> IBinder?): Boolean {
            try {
                val t = f()
                if (t != null) { token = t; tokenHow = name; return true }
                errs += "$name=null"
            } catch (e: Throwable) { errs += "$name:${root(e)}" }
            return false
        }

        if (attempt("SurfaceControl.getInternalDisplayToken") { sc.getMethod("getInternalDisplayToken").invoke(null) as IBinder? }) return token
        if (attempt("SurfaceControl.getPhysicalDisplayToken") {
                val ids = sc.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
                sc.getMethod("getPhysicalDisplayToken", LONG).invoke(null, ids[0]) as IBinder?
            }) return token
        for (name in listOf("android.view.DisplayControl", "com.android.server.display.DisplayControl")) {
            if (attempt("$name (framework)") { fromDisplayControl(Class.forName(name)) }) return token
        }
        if (attempt("DisplayControl (services.jar)") { fromDisplayControl(loadDisplayControlFromServices()) }) return token
        if (attempt("SurfaceControl.getBuiltInDisplay(0)") { sc.getMethod("getBuiltInDisplay", INT).invoke(null, 0) as IBinder? }) return token

        tokenHow = "thất bại: " + errs.joinToString(" | ")
        return null
    }

    private fun fromDisplayControl(dc: Class<*>): IBinder? {
        val ids = dc.getMethod("getPhysicalDisplayIds").invoke(null) as LongArray
        return dc.getMethod("getPhysicalDisplayToken", LONG).invoke(null, ids[0]) as IBinder?
    }

    /** Android 14+: DisplayControl nằm trong services.jar, phải nạp libandroid_servers (cách scrcpy làm). */
    private fun loadDisplayControlFromServices(): Class<*> {
        val loader: ClassLoader = try {
            val f = Class.forName("com.android.internal.os.ClassLoaderFactory")
            val m = f.getDeclaredMethod(
                "createClassLoader", String::class.java, String::class.java, String::class.java,
                ClassLoader::class.java, INT, Boolean::class.javaPrimitiveType, String::class.java
            )
            m.invoke(null, "/system/framework/services.jar", null, null, ClassLoader.getSystemClassLoader(), 0, true, null) as ClassLoader
        } catch (_: Throwable) {
            dalvik.system.PathClassLoader("/system/framework/services.jar", ClassLoader.getSystemClassLoader())
        }
        val dc = loader.loadClass("com.android.server.display.DisplayControl")
        val load = Runtime::class.java.getDeclaredMethod("loadLibrary0", Class::class.java, String::class.java)
        load.isAccessible = true
        load.invoke(Runtime.getRuntime(), dc, "android_servers")
        return dc
    }

    /** @return tên cách đã dùng, hoặc null nếu mọi cách đều lỗi (lỗi ở [lastErr]). */
    private fun setProjection(tk: IBinder, orientation: Int, stack: Rect, disp: Rect): String? {
        val errs = ArrayList<String>()

        // Cách 1 (Android 12+): SurfaceControl.Transaction
        try {
            val tc = Class.forName("android.view.SurfaceControl\$Transaction")
            val tx = tc.getConstructor().newInstance()
            tc.getMethod("setDisplayProjection", IBinder::class.java, INT, Rect::class.java, Rect::class.java)
                .invoke(tx, tk, orientation, stack, disp)
            tc.getMethod("apply").invoke(tx)
            try { tc.getMethod("close").invoke(tx) } catch (_: Throwable) {}
            applyHow = "Transaction.setDisplayProjection"
            lastErr = ""
            return applyHow
        } catch (e: Throwable) { errs += "Transaction:${root(e)}" }

        // Cách 2 (Android 10-13): hàm tĩnh trong transaction toàn cục
        try {
            sc.getMethod("openTransaction").invoke(null)
            try {
                sc.getMethod("setDisplayProjection", IBinder::class.java, INT, Rect::class.java, Rect::class.java)
                    .invoke(null, tk, orientation, stack, disp)
            } finally {
                sc.getMethod("closeTransaction").invoke(null)
            }
            applyHow = "SurfaceControl.setDisplayProjection (tĩnh)"
            lastErr = ""
            return applyHow
        } catch (e: Throwable) { errs += "tĩnh:${root(e)}" }

        lastErr = errs.joinToString(" | ")
        return null
    }
}
