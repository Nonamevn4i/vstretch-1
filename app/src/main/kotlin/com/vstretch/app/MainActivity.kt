package com.vstretch.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var dev: Dev? = null
    private var bigTv: TextView? = null
    private var infoTv: TextView? = null
    private var seek: SeekBar? = null

    private val binderL = Shizuku.OnBinderReceivedListener { runOnUiThread { Shizu.ensureBound(); reload() } }
    private val deadL = Shizuku.OnBinderDeadListener { runOnUiThread { reload() } }
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { Shizu.ensureBound(); reload() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        Shizuku.addBinderReceivedListenerSticky(binderL)
        Shizuku.addBinderDeadListener(deadL)
        Shizuku.addRequestPermissionResultListener(permL)
        buildUi()
    }

    override fun onResume() { super.onResume(); Shizu.ensureBound(); reload() }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderL)
        Shizuku.removeBinderDeadListener(deadL)
        Shizuku.removeRequestPermissionResultListener(permL)
        super.onDestroy()
    }

    override fun onConfigurationChanged(c: android.content.res.Configuration) { super.onConfigurationChanged(c); buildUi() }

    private fun reload() = Engine.refresh(this) { dev = it; buildUi() }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun dp(v: Int) = Ui.dp(this, v)

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val d = dev
        val cards = listOf(statusCard(), deviceCard(d), stretchCard(d), gameCard(), floatCard())
        val pad = dp(10)
        val body = LinearLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            if (resources.configuration.screenWidthDp >= 720) {   // tablet: 2 cột
                orientation = LinearLayout.HORIZONTAL
                fun col(vararg v: View) = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; v.forEach { addView(it) } }
                addView(col(cards[0], cards[1], cards[4]), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(col(cards[2], cards[3]), LinearLayout.LayoutParams(0, WRAP, 1.25f))
            } else {
                orientation = LinearLayout.VERTICAL
                cards.forEach { addView(it) }
            }
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(body) })
    }

    private fun card(title: String, vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(Ui.CARD, dp(16).toFloat())
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
        addView(Ui.tv(context, title, 16f, Ui.TEXT, true).apply { setPadding(0, 0, 0, dp(8)) })
        views.forEach { addView(it) }
    }

    private fun kv(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3))
        addView(Ui.tv(context, k, 13f, Ui.MUTED), LinearLayout.LayoutParams(0, WRAP, 0.42f))
        addView(Ui.tv(context, v, 13f), LinearLayout.LayoutParams(0, WRAP, 0.58f))
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
        v.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
    }

    private fun note(s: String) = Ui.tv(this, s, 12f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) }

    // ---- Shizuku
    private fun statusCard(): View {
        val run = Shizu.running(); val ok = Shizu.granted()
        val msg = when {
            !run -> "Shizuku chưa chạy. Mở Shizuku rồi bấm Start (qua Gỡ lỗi không dây hoặc root)."
            !ok -> "Shizuku đang chạy nhưng chưa cấp quyền cho StretchX."
            else -> "Đã kết nối Shizuku ✔ (quyền shell)"
        }
        val c = card("Kết nối Shizuku", Ui.tv(this, msg, 13f, if (ok) Ui.OK else Ui.TEXT))
        if (run && !ok) c.addView(row(Ui.btn(this, "Cấp quyền") { Shizu.requestPermission(100) }))
        if (!run) c.addView(row(Ui.btn(this, "Mở Shizuku") { openShizuku() }))
        return c
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        startActivity(i)
    }

    // ---- Thông tin máy
    private fun deviceCard(d: Dev?): View {
        val c = card("Thông tin thiết bị")
        if (d == null) { c.addView(Ui.tv(this, "Đang đọc thông tin…", 13f, Ui.MUTED)); return c }
        fun wh(w: Int, h: Int) = "${max(w, h)}×${min(w, h)}"
        c.addView(kv("Máy", d.model))
        c.addView(kv("Hệ điều hành", d.android))
        c.addView(kv("Chip", d.soc))
        c.addView(kv("ABI", d.abi))
        c.addView(kv("RAM", String.format(Locale.US, "%.1f GB", d.ramGb)))
        c.addView(kv("Độ phân giải gốc", "${wh(d.physW, d.physH)} px"))
        c.addView(kv("Đang dùng", "${wh(d.curW, d.curH)} px" + if (d.overridden) "  (đã chỉnh)" else ""))
        c.addView(kv("Tỉ lệ khung", d.aspect))
        c.addView(kv("Kích thước", String.format(Locale.US, "≈ %.2f inch", d.inches)))
        c.addView(kv("Mật độ (DPI)", if (d.curDpi == d.physDpi) "${d.physDpi}" else "${d.curDpi} (gốc ${d.physDpi})"))
        c.addView(kv("Tần số quét", String.format(Locale.US, "%.0f Hz — hỗ trợ: %s", d.hz, d.modes)))
        if (d.redmagic) c.addView(note("Đã nhận diện thiết bị RedMagic/Nubia ✔"))
        if (!d.viaShizuku) c.addView(note("Chưa có Shizuku: số liệu lấy từ API Android, có thể thiếu thông tin phần đã chỉnh."))
        return c
    }

    // ---- Kéo dãn
    private fun stretchCard(d: Dev?): View {
        bigTv = Ui.tv(this, fmtK(prefs.k), 40f, Ui.RED, true).apply { gravity = Gravity.CENTER }
        infoTv = Ui.tv(this, "", 13f, Ui.TEXT).apply { setPadding(0, dp(8), 0, 0) }
        seek = SeekBar(this).apply {
            max = 50; progress = ((prefs.k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.k = 1f + p / 100f; updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        val c = card("Kéo dãn màn hình", bigTv!!, seek!!)

        // Preset thông minh theo tỉ lệ máy
        val presets = ArrayList<View>()
        presets.add(Ui.btn(this, "Gốc 1.00x", Ui.GRAY) { prefs.k = 1f; buildUi() })
        if (d != null) {
            val a = max(d.physW, d.physH).toFloat() / min(d.physW, d.physH)
            listOf("16:9" to 16f / 9, "2:1" to 2f, "21:9" to 21f / 9).forEach { (name, t) ->
                val k = (t / a * 100).roundToInt() / 100f
                if (k in 1.01f..Stretch.MAX) presets.add(Ui.btn(this, "$name · ${fmtK(k)}", Ui.GRAY) { prefs.wide = true; prefs.k = k; buildUi() })
            }
        }
        c.addView(row(*presets.toTypedArray()))

        c.addView(row(
            Ui.btn(this, "Rộng (viền trên/dưới)", if (prefs.wide) Ui.RED else Ui.GRAY) { prefs.wide = true; buildUi() },
            Ui.btn(this, "Hẹp (viền trái/phải)", if (!prefs.wide) Ui.RED else Ui.GRAY) { prefs.wide = false; buildUi() }
        ))
        c.addView(row(
            Ui.btn(this, if (prefs.fill) "Kéo giãn thật: BẬT" else "Kéo giãn thật: TẮT", if (prefs.fill) Ui.RED else Ui.GRAY) { prefs.fill = !prefs.fill; buildUi() },
            Ui.btn(this, "Chẩn đoán", Ui.GRAY) { showDiag() }
        ))
        c.addView(row(*listOf(100, 90, 80, 70, 60).map { r ->
            Ui.btn(this, "$r%", if (prefs.res == r) Ui.RED else Ui.GRAY) { prefs.res = r; buildUi() }
        }.toTypedArray()))
        c.addView(infoTv!!)
        c.addView(row(
            Ui.btn(this, "Áp dụng") { Engine.apply(this, prefs.k, prefs.wide, prefs.res) { m -> toast(m); reload() } },
            Ui.btn(this, "Reset mặc định", Ui.GRAY) { Engine.reset(this) { m -> toast(m); reload() } }
        ))
        c.addView(note("Hàng 100%–60% là tỉ lệ độ phân giải (giảm để nhẹ máy hơn). DPI tự bù để giao diện không đổi kích thước."))
        c.addView(note("“Kéo giãn thật” ép SurfaceFlinger kéo hình ra TOÀN BỘ panel (hết viền đen). Game vẫn thấy màn hình 16:9 nên không bị khóa tỉ lệ. " +
            "Lưu ý: cảm ứng vẫn tính theo vùng gốc nên càng gần mép càng lệch nhẹ — hãy dùng mức kéo vừa phải."))
        updatePreview()
        return c
    }

    private fun updatePreview() {
        val d = dev
        val p = Engine.planFor(this, prefs.k, prefs.wide, prefs.res)
        if (p != null && abs1(p.k - prefs.k)) {            // bị kẹp theo giới hạn độ phân giải của máy
            prefs.k = p.k; seek?.progress = ((p.k - 1f) * 100).roundToInt()
        }
        bigTv?.text = fmtK(prefs.k)
        infoTv?.text = if (p == null || d == null) "Chưa đọc được thông tin máy" else
            "Màn hình ảo: ${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)})\n" +
            "DPI: ${if (prefs.active && prefs.baseDpi > 0) prefs.baseDpi else d.curDpi} → ${p.density}\n" +
            (if (prefs.fill && !p.isDefault)
                "Viền đen: 0% — lấp đầy bằng kéo giãn ${if (prefs.wide) "dọc" else "ngang"} ${fmtK(p.k)}\n"
            else
                String.format(Locale.US, "Viền đen: ≈ %.1f%% %s\n", p.barPct, if (prefs.wide) "(trên/dưới)" else "(trái/phải)")) +
            "Mức kéo tối đa an toàn cho máy này: ${fmtK(p.maxK)}"
    }

    private fun abs1(x: Float) = kotlin.math.abs(x) > 0.004f

    private fun showDiag() = Engine.diag { text ->
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Chẩn đoán kéo giãn")
            .setMessage(text)
            .setPositiveButton("Đóng", null)
            .show()
    }

    // ---- Game
    private fun gameCard(): View {
        val pkg = prefs.game
        val label = try { packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString() } catch (_: Exception) { null }
        val c = card("Game", Ui.tv(this, if (label != null) "$label\n$pkg — đã cài ✔" else "$pkg\nChưa cài hoặc không thấy", 13f, if (label != null) Ui.OK else Ui.TEXT))
        c.addView(row(
            Ui.btn(this, "Mở game & áp dụng") { Engine.apply(this, prefs.k, prefs.wide, prefs.res) { m -> toast(m); Handler(Looper.getMainLooper()).postDelayed({ launchGame() }, 700) } },
            Ui.btn(this, "Chọn game khác", Ui.GRAY) { pickGame() }
        ))
        if (pkg != Prefs.GAME_VALORANT_CN) c.addView(row(Ui.btn(this, "Dùng Valorant CN (无畏契约)", Ui.GRAY) { prefs.game = Prefs.GAME_VALORANT_CN; buildUi() }))
        c.addView(note("Nên áp dụng TRƯỚC khi vào game. Đổi độ phân giải giữa trận có thể làm game tải lại hoặc thoát."))
        return c
    }

    private fun launchGame() {
        val i = packageManager.getLaunchIntentForPackage(prefs.game)
        if (i == null) toast("Không mở được ${prefs.game}") else startActivity(i)
    }

    @Suppress("DEPRECATION")
    private fun pickGame() {
        val pm = packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Chọn game")
            .setItems(list.map { "${it.second}\n${it.first}" }.toTypedArray()) { _, i -> prefs.game = list[i].first; buildUi() }
            .show()
    }

    // ---- Cửa sổ nổi
    private fun floatCard(): View {
        val can = Settings.canDrawOverlays(this)
        val c = card("Cửa sổ nổi (pop-up)", Ui.tv(this, if (can) "Quyền hiển thị trên ứng dụng khác: đã cấp ✔" else "Cần cấp quyền hiển thị trên ứng dụng khác.", 13f, if (can) Ui.OK else Ui.TEXT))
        if (!can) c.addView(row(Ui.btn(this, "Cấp quyền") { grantOverlay() }))
        c.addView(row(
            Ui.btn(this, "Bật cửa sổ nổi") { startFloat() },
            Ui.btn(this, "Tắt", Ui.GRAY) { stopService(Intent(this, FloatingService::class.java)) }
        ))
        c.addView(note("Bong bóng nổi kéo thả được: chạm để mở thanh chỉnh 1.00x–1.50x và nút Reset. Thông báo cũng có nút “Reset màn hình”."))
        return c
    }

    private fun grantOverlay() {
        if (Shizu.granted()) Shizu.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow") { reload() }
        else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startFloat() {
        if (!Settings.canDrawOverlays(this)) { grantOverlay(); toast("Hãy cấp quyền rồi bấm lại"); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        startForegroundService(Intent(this, FloatingService::class.java))
        toast("Đã bật bong bóng nổi")
    }
}
