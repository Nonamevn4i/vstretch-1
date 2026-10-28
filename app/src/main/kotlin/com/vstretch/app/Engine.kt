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
            val rd = restoreDensity(p, d)   // khôi phục khẩn cấp (Volume+) cũng dùng đúng DPI khi reset
            Shizu.call({
                it.setTouch(
                    wantTouch, lnW, lnH, d.physW, d.physH,
                    p.baseW, p.baseH, rd.first, p.baseSizeOverride, rd.second
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
                        // Một số ROM tự đổi lại DPI sau khi đổi `wm size` -> kiểm tra và ép lại nếu lệch
                        fixDensity(app, restored) { fin ->
                            Shizu.call({ it.setStretch(fin.physW, fin.physH, fin.physW, fin.physH, false) }) { r ->
                                stretchMsg = r
                                val (dpi, _) = restoreDensity(p, fin)
                                cb("Đã reset về thiết lập màn hình trước khi áp dụng StretchX (${fin.curDpi} dpi" +
                                    (if (fin.curDpi != dpi && p.resetDpi > 0) " — ROM không nhận $dpi dpi" else "") + ")")
                            }
                        }
                    }
                }, 350)
            }
        }
    }

    /**
     * DPI sẽ dùng khi khôi phục: (dpi, có cần `wm density <dpi>` không).
     * Ưu tiên DPI người dùng chọn (Prefs.resetDpi); không có thì dùng DPI trước khi app can thiệp.
     */
    fun restoreDensity(p: Prefs, d: Dev): Pair<Int, Boolean> {
        if (p.resetDpi > 0) return p.resetDpi to (p.resetDpi != d.physDpi)
        val ov = p.baseDensityOverride || (p.baseDpi > 0 && p.baseDpi != d.physDpi)
        return if (ov && p.baseDpi > 0) p.baseDpi to true else d.physDpi to false
    }

    /** Sau khi reset: nếu DPI thực tế chưa đúng mục tiêu thì ép lại một lần rồi đọc lại. */
    private fun fixDensity(app: Context, d: Dev, done: (Dev) -> Unit) {
        val (dpi, ov) = restoreDensity(Prefs(app), d)
        val want = if (ov) dpi else d.physDpi
        if (d.curDpi == want) { done(d); return }
        Shizu.exec(if (ov) "wm density $dpi" else "wm density reset") {
            main.postDelayed({ refresh(app) { done(it) } }, 250)
        }
    }

    /**
     * Áp lại phép chiếu kéo giãn NGAY (không đổi wm size/density, không đụng cảm ứng).
     * Dùng sau khi thêm/bỏ cửa sổ nổi để hình không bị nháy về kiểu có viền đen.
     */
    fun resync(ctx: Context) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        val d = dev ?: return
        if (!p.active || !p.fill || !Shizu.granted()) return
        val plan = Stretch.plan(d.physW, d.physH, if (p.baseDpi > 0) p.baseDpi else d.curDpi, p.k, p.wide, p.res)
        if (plan.isDefault) return
        Shizu.call({ it.setStretch(plan.lnW, plan.lnH, d.physW, d.physH, true) }) {}
    }

    private fun restoreCommand(p: Prefs, d: Dev): String {
        val size = if (p.baseSizeOverride && p.baseW > 0 && p.baseH > 0) {
            "wm size ${p.baseW}x${p.baseH}"
        } else {
            "wm size reset"
        }
        val (dpi, ov) = restoreDensity(p, d)
        val density = if (ov) "wm density $dpi" else "wm density reset"
        return "$size; $density"
    }
}
