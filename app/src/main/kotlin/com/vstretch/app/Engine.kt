package com.vstretch.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Logic trung tâm: đọc máy, tính kế hoạch, gọi `wm size` / `wm density` rồi kéo giãn thật qua SurfaceFlinger (Sf.kt). */
object Engine {
    @Volatile var dev: Dev? = null

    /** Kết quả lần kéo giãn gần nhất (hiện trong UI). */
    @Volatile var stretchMsg: String = ""
    @Volatile var touchMsg: String = ""

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
            if (!p.active) {
                val previousBaseDpi = p.baseDpi
                p.baseDpi = if (d.curDpi == d.physDpi && previousBaseDpi > 0 && previousBaseDpi != d.physDpi) {
                    previousBaseDpi
                } else {
                    d.curDpi
                }
                p.baseW = d.curW
                p.baseH = d.curH
                p.baseSizeOverride = d.curW != d.physW || d.curH != d.physH
                p.baseDensityOverride = p.baseDpi != d.physDpi
            } else if (p.baseDpi <= 0) {
                p.baseDpi = d.curDpi
            } else if (p.baseDpi != d.physDpi) {
                p.baseDensityOverride = true
            }
            val plan = Stretch.plan(d.physW, d.physH, p.baseDpi, k, wide, res)
            val cmd = if (plan.isDefault) restoreCommand(p, d)
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
                                val head = if (plan.isDefault) "Đã khôi phục thiết lập trước đó"
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
        val p = Prefs(app)
        val lnW = if (fill) plan.lnW else d.physW   // khi tắt: màn hình logic chỉ để tính vùng vừa khít mặc định
        val lnH = if (fill) plan.lnH else d.physH
        Shizu.call({ it.setStretch(lnW, lnH, d.physW, d.physH, fill) }) { r ->
            stretchMsg = r
            val head = when {
                r.startsWith("ERR") -> "⚠ Kéo giãn thật KHÔNG chạy được → vẫn còn viền đen.\n${r.removePrefix("ERR: ")}\n(Mở “Chẩn đoán” để xem chi tiết)"
                fill -> "✔ Đã kéo giãn lấp đầy màn hình (không viền đen)"
                else -> ""
            }
            // Sửa cảm ứng chỉ bật khi hình đã kéo giãn thành công; mọi trường hợp khác đều nhả thiết bị chạm
            val wantTouch = fill && !r.startsWith("ERR") && p.touch
            Shizu.call({
                it.setTouch(
                    wantTouch, lnW, lnH, d.physW, d.physH,
                    p.baseW, p.baseH, p.baseDpi, p.baseSizeOverride, p.baseDensityOverride
                )
            }) { tr ->
                touchMsg = tr
                cb(head + when {
                    !wantTouch -> ""
                    tr.startsWith("ERR") -> "\n⚠ Sửa cảm ứng KHÔNG chạy: ${tr.removePrefix("ERR: ")}"
                    else -> "\n✔ Đã sửa cảm ứng (2 cạnh ấn/vuốt được). Kẹt cảm ứng: bấm Volume+ 3 lần."
                })
            }
        }
    }

    fun diag(cb: (String) -> Unit) {
        if (!Shizu.granted()) { cb("Shizuku chưa chạy / chưa cấp quyền"); return }
        Shizu.call({ it.diag() + "\n\n── Sửa cảm ứng ──\n" + it.touchDiag() }) { r -> cb(r + if (stretchMsg.isNotEmpty()) "\n\nKết quả gần nhất: $stretchMsg" else "") }
    }

    fun reset(ctx: Context, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (!Shizu.granted()) { cb("Cần Shizuku để reset (hoặc chạy: adb shell wm size reset)"); return }
        Shizu.call({ it.setTouch(false, 0, 0, 0, 0, 0, 0, 0, false, false) }) {}   // nhả cảm ứng TRƯỚC khi đổi lại hiển thị
        refresh(app) { d ->
            Shizu.exec(restoreCommand(p, d)) {
                p.active = false; p.k = 1f
                // trả phép chiếu SurfaceFlinger về mặc định và dừng canh gác
                main.postDelayed({
                    refresh(app) { restored ->
                        Shizu.call({ it.setStretch(restored.physW, restored.physH, restored.physW, restored.physH, false) }) { r ->
                            stretchMsg = r
                            cb("Đã reset về thiết lập màn hình trước khi áp dụng StretchX")
                        }
                    }
                }, 350)
            }
        }
    }

    private fun restoreCommand(p: Prefs, d: Dev): String {
        if (!p.baseDensityOverride && p.baseDpi > 0 && p.baseDpi != d.physDpi) {
            p.baseDensityOverride = true
        }
        val size = if (p.baseSizeOverride && p.baseW > 0 && p.baseH > 0) {
            "wm size ${p.baseW}x${p.baseH}"
        } else {
            "wm size reset"
        }
        val density = if (p.baseDensityOverride && p.baseDpi > 0) {
            "wm density ${p.baseDpi}"
        } else {
            "wm density reset"
        }
        return "$size; $density"
    }
}
