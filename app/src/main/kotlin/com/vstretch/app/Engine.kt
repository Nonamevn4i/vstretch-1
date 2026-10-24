package com.vstretch.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Logic trung tâm: đọc máy, tính kế hoạch, gọi `wm size` / `wm density` rồi kéo giãn thật qua SurfaceFlinger (Sf.kt). */
object Engine {
    @Volatile var dev: Dev? = null

    /** Kết quả lần kéo giãn gần nhất (hiện trong UI). */
    @Volatile var stretchMsg: String = ""

    private val main = Handler(Looper.getMainLooper())

    fun refresh(ctx: Context, cb: (Dev) -> Unit) {
        val app = ctx.applicationContext
        fun finish(out: String?) {
            val d = DeviceInfo.read(app, out?.takeIf { !it.startsWith("ERR") })
            dev = d
            if (!d.overridden) Prefs(app).active = false
            cb(d)
        }
        if (Shizu.granted()) Shizu.exec("wm size; wm density") { finish(it) } else finish(null)
    }

    /** Xem trước kế hoạch với dữ liệu máy đã đọc. */
    fun planFor(ctx: Context, k: Float, wide: Boolean, res: Int): Plan? {
        val d = dev ?: return null
        val p = Prefs(ctx)
        val base = if (p.active && p.baseDpi > 0) p.baseDpi else d.curDpi
        return Stretch.plan(d.physW, d.physH, base, k, wide, res)
    }

    fun apply(ctx: Context, k: Float, wide: Boolean, res: Int, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (!Shizu.granted()) { cb("Cần bật Shizuku và cấp quyền cho StretchX"); return }
        refresh(app) { d ->
            if (!p.active || p.baseDpi <= 0) p.baseDpi = d.curDpi
            val plan = Stretch.plan(d.physW, d.physH, p.baseDpi, k, wide, res)
            val cmd = if (plan.isDefault) "wm size reset; wm density reset"
                      else "wm size ${plan.wmSize}; wm density ${plan.density}"
            Shizu.exec(cmd) { out ->
                p.active = !plan.isDefault
                p.k = plan.k
                if (out.startsWith("ERR")) {
                    refresh(app) { cb(out) }
                } else {
                    // Chờ DisplayManager nhận kích thước mới rồi mới kéo giãn (Sf.set còn tự chờ thêm nếu cần)
                    main.postDelayed({
                        stretch(app, plan, d, p.fill && !plan.isDefault) { sMsg ->
                            refresh(app) {
                                val head = if (plan.isDefault) "Đã về mặc định"
                                           else "Đã áp dụng ${plan.wmSize} @${plan.density}dpi (${fmtK(plan.k)})"
                                cb("$head\n$sMsg")
                            }
                        }
                    }, 350)
                }
            }
        }
    }

    /** fill=true: lấp đầy panel bằng kéo giãn thật; fill=false: trả về phép chiếu mặc định. */
    private fun stretch(app: Context, plan: Plan, d: Dev, fill: Boolean, cb: (String) -> Unit) {
        if (!Shizu.granted()) { stretchMsg = ""; cb(""); return }
        val lnW = if (fill) plan.lnW else d.physW   // khi tắt: màn hình logic chỉ để tính vùng vừa khít mặc định
        val lnH = if (fill) plan.lnH else d.physH
        Shizu.call({ it.setStretch(lnW, lnH, d.physW, d.physH, fill) }) { r ->
            stretchMsg = r
            cb(
                when {
                    r.startsWith("ERR") -> "⚠ Kéo giãn thật KHÔNG chạy được → vẫn còn viền đen.\n${r.removePrefix("ERR: ")}\n(Mở “Chẩn đoán” để xem chi tiết)"
                    fill -> "✔ Đã kéo giãn lấp đầy màn hình (không viền đen)"
                    else -> ""
                }
            )
        }
    }

    fun diag(cb: (String) -> Unit) {
        if (!Shizu.granted()) { cb("Shizuku chưa chạy / chưa cấp quyền"); return }
        Shizu.call({ it.diag() }) { r -> cb(r + if (stretchMsg.isNotEmpty()) "\n\nKết quả gần nhất: $stretchMsg" else "") }
    }

    fun reset(ctx: Context, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (!Shizu.granted()) { cb("Cần Shizuku để reset (hoặc chạy: adb shell wm size reset)"); return }
        Shizu.exec("wm size reset; wm density reset") {
            p.active = false; p.k = 1f
            // trả phép chiếu SurfaceFlinger về mặc định và dừng canh gác
            main.postDelayed({
                refresh(app) { d ->
                    Shizu.call({ it.setStretch(d.physW, d.physH, d.physW, d.physH, false) }) { r ->
                        stretchMsg = r
                        cb("Đã reset về màn hình mặc định")
                    }
                }
            }, 350)
        }
    }
}
