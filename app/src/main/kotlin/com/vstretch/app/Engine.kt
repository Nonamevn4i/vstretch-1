package com.vstretch.app

import android.content.Context

/** Logic trung tâm: đọc máy, tính kế hoạch, gọi `wm size` / `wm density` qua Shizuku. */
object Engine {
    @Volatile var dev: Dev? = null

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
                refresh(app) {
                    cb(if (out.startsWith("ERR")) out
                       else if (plan.isDefault) "Đã về mặc định"
                       else "Đã áp dụng ${plan.wmSize} @${plan.density}dpi (${fmtK(plan.k)})")
                }
            }
        }
    }

    fun reset(ctx: Context, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (!Shizu.granted()) { cb("Cần Shizuku để reset (hoặc chạy: adb shell wm size reset)"); return }
        Shizu.exec("wm size reset; wm density reset") {
            p.active = false; p.k = 1f
            refresh(app) { cb("Đã reset về màn hình mặc định") }
        }
    }
}
