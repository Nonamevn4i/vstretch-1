package com.vstretch.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

private fun wh(w: Int, h: Int) = "${max(w, h)}×${min(w, h)}"

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var dev: Dev? = null
    private var bigTv: TextView? = null
    private var infoTv: TextView? = null
    private var seek: SeekBar? = null
    private var sv: ScrollView? = null
    private val ui = Handler(Looper.getMainLooper())

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
    private fun dlg() = AlertDialog.Builder(this, R.style.Cyber_Dialog)

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val keep = sv?.scrollY ?: 0      // giữ vị trí cuộn khi dựng lại giao diện
        val d = dev
        val status = statusCard(d)
        val quick = quickCard()
        val feat = featureCard()
        val stretch = stretchCard(d)
        val games = gameCard()
        val tiles = tileCard()
        val fl = floatCard()
        val hist = historyCard()
        val devC = deviceCard(d)
        val pad = dp(10)
        val body = LinearLayout(this).apply {
            setPadding(pad, pad, pad, pad)
            if (resources.configuration.screenWidthDp >= 720) {   // tablet: 2 cột
                orientation = LinearLayout.HORIZONTAL
                fun col(vararg v: View) = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; v.forEach { addView(it) } }
                addView(col(status, quick, feat, games, hist), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(col(stretch, tiles, fl, devC), LinearLayout.LayoutParams(0, WRAP, 1.25f))
            } else {
                orientation = LinearLayout.VERTICAL
                listOf(status, quick, feat, games, stretch, tiles, fl, hist, devC).forEach { addView(it) }
            }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.header(this@MainActivity, "TRUE STRETCH · SHIZUKU · REDMAGIC EDITION · v${BuildConfig.VERSION_NAME}"))
            addView(body)
        }
        val s = ScrollView(this).apply { background = Ui.carbon(this@MainActivity); addView(page) }
        sv = s
        setContentView(s)
        if (keep > 0) s.post { s.scrollTo(0, keep) }
    }

    private fun card(title: String, vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.panel(context)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
        addView(Ui.title(context, title))
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

    // ---- Trạng thái + Shizuku + độ phân giải hiện tại
    private fun statusCard(d: Dev?): View {
        val run = Shizu.running(); val ok = Shizu.granted()
        val msg = when {
            !run -> "Shizuku chưa chạy. Mở Shizuku rồi bấm Start (qua Gỡ lỗi không dây hoặc root)."
            !ok -> "Shizuku đang chạy nhưng chưa cấp quyền cho StretchX."
            else -> "Đã kết nối Shizuku ✔ (quyền shell)"
        }
        val c = card("Trạng thái", Ui.tv(this, msg, 13f, if (ok) Ui.OK else Ui.TEXT))
        if (run && !ok) c.addView(row(Ui.btn(this, "Cấp quyền") { Shizu.requestPermission(100) }))
        if (!run) c.addView(row(Ui.btn(this, "Mở Shizuku") { openShizuku() }))
        if (d != null) {
            c.addView(Ui.tv(this, "ĐỘ PHÂN GIẢI HIỆN TẠI", 10f, Ui.DIM, true).apply { letterSpacing = 0.14f; setPadding(0, dp(12), 0, 0) })
            c.addView(Ui.tv(this, wh(d.curW, d.curH).replace("×", " × "), 26f, if (d.overridden) Ui.RED else Ui.TEXT, true))
            val ap = Profiles.all(this).firstOrNull { Profiles.isActive(this, it) }
            val st = when {
                !prefs.active -> "đang ở mặc định"
                ap != null -> "hồ sơ “${ap.name}” đang bật"
                else -> "chỉnh tay ${fmtK(prefs.k)}"
            }
            c.addView(Ui.tv(this, "${d.curDpi} dpi · $st", 12f, Ui.MUTED))
        }
        return c
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        startActivity(i)
    }

    // ---- Hồ sơ nhanh (Quick presets)
    private fun quickCard(): View {
        val list = Profiles.all(this)
        val c = card("Hồ sơ nhanh")
        c.addView(Ui.tv(this, "Chạm = bật hồ sơ đã lưu (không cần kéo tay); chạm lần nữa = khôi phục. Giữ lâu để sửa / gán phím tắt.", 12f, Ui.MUTED))
        if (list.isEmpty()) c.addView(note("Chưa có hồ sơ nào. Bấm “Lưu cài đặt hiện tại” để tạo."))
        list.chunked(4).forEach { chunk ->
            val views = chunk.map { chip(it) }.toMutableList<View>()
            while (views.size < 4) views.add(View(this))
            c.addView(row(*views.toTypedArray()))
        }
        c.addView(row(
            Ui.btn(this, "+ Lưu hiện tại", Ui.GRAY) { saveCurrent() },
            Ui.btn(this, "Tùy chỉnh ✎", Ui.GRAY) { manageDialog() },
            Ui.btn(this, "Khôi phục", Ui.GRAY) { Actions.restore(this, "app") { m -> toast(m); reload() } }
        ))
        return c
    }

    private fun chip(p: Profile): Button {
        val on = Profiles.isActive(this, p)
        val b = Ui.btn(this, p.name, if (on) Ui.RED else Ui.GRAY) { toggleProfile(p) }
        b.maxLines = 1
        b.ellipsize = TextUtils.TruncateAt.END
        b.setOnLongClickListener { profileMenu(p); true }
        return b
    }

    private fun toggleProfile(p: Profile) = Actions.toggleProfile(this, p, "app") { m -> toast(m); reload() }

    private fun saveCurrent() =
        editProfile(Profile(Profiles.newId(), fmtK(prefs.k), prefs.k, prefs.wide, prefs.res, prefs.fill, prefs.touch))

    private fun manageDialog() {
        val list = Profiles.all(this)
        if (list.isEmpty()) { toast("Chưa có hồ sơ nào"); return }
        dlg().setTitle("Hồ sơ đã lưu")
            .setItems(list.map { "${it.name} — ${it.summary()}" }.toTypedArray()) { _, i -> profileMenu(list[i]) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun profileMenu(p: Profile) {
        fun mark(s: Int) = if (Profiles.forSlot(this, s)?.id == p.id) " ✔" else ""
        val items = arrayOf(
            "Bật ngay", "Sửa hồ sơ",
            "Gán cho phím tắt ô 1${mark(0)}", "Gán cho phím tắt ô 2${mark(1)}", "Gán cho phím tắt ô 3${mark(2)}",
            "Xóa hồ sơ"
        )
        dlg().setTitle("${p.name} — ${p.summary()}")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> toggleProfile(p)
                    1 -> editProfile(p)
                    2, 3, 4 -> { prefs.setTile(i - 2, p.id); toast("Ô phím tắt ${i - 1} → ${p.name}"); buildUi() }
                    5 -> confirmDelete(p)
                }
            }
            .show()
    }

    private fun confirmDelete(p: Profile) {
        dlg().setTitle("Xóa hồ sơ “${p.name}”?")
            .setPositiveButton("Xóa") { _, _ -> Profiles.delete(this, p.id); buildUi() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /** Hộp thoại tạo / sửa hồ sơ (id chưa có trong danh sách = tạo mới). */
    private fun editProfile(init: Profile) {
        var k = init.k; var wide = init.wide; var res = init.res; var fill = init.fill; var touch = init.touch
        val isNew = Profiles.get(this, init.id) == null

        val name = EditText(this).apply {
            setText(init.name); setTextColor(Ui.TEXT); setHintTextColor(Ui.DIM); hint = "Tên hồ sơ"
            setSingleLine(); textSize = 16f; setSelectAllOnFocus(true)
        }
        val kTv = Ui.tv(this, fmtK(k), 30f, Ui.RED, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        val infoT = Ui.tv(this, "", 12f, Ui.MUTED)
        fun upd() {
            kTv.text = fmtK(k)
            val p = Engine.planFor(this@MainActivity, k, wide, res)
            infoT.text = if (p == null) "" else "Màn hình ảo ${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)}) · tối đa ${fmtK(p.maxK)}"
        }
        val sk = SeekBar(this).apply {
            Ui.seek(this)
            max = 50; progress = ((k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { k = 1f + p / 100f; upd() }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        fun dirT() = if (wide) "Hướng: Rộng" else "Hướng: Hẹp"
        fun resT() = "Độ phân giải: $res%"
        fun fillT() = "Kéo giãn thật: " + if (fill) "BẬT" else "TẮT"
        fun touchT() = "Sửa cảm ứng: " + if (touch) "BẬT" else "TẮT"
        val dirB = Ui.btn(this, dirT(), Ui.GRAY) {}
        val resB = Ui.btn(this, resT(), Ui.GRAY) {}
        val fillB = Ui.btn(this, fillT(), Ui.GRAY) {}
        val touchB = Ui.btn(this, touchT(), Ui.GRAY) {}
        dirB.setOnClickListener { wide = !wide; dirB.text = dirT(); upd() }
        resB.setOnClickListener {
            val opts = listOf(100, 90, 80, 70, 60)
            res = opts[(opts.indexOf(res) + 1).mod(opts.size)]
            resB.text = resT(); upd()
        }
        fillB.setOnClickListener { fill = !fill; fillB.text = fillT() }
        touchB.setOnClickListener { touch = !touch; touchB.text = touchT() }
        upd()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(name); addView(kTv); addView(sk); addView(infoT)
            addView(row(dirB, resB)); addView(row(fillB, touchB))
        }
        dlg().setTitle(if (isNew) "Lưu hồ sơ mới" else "Sửa hồ sơ")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Lưu") { _, _ ->
                val nm = name.text.toString().trim().ifEmpty { fmtK(k) }
                Profiles.save(this, Profile(init.id, nm, k, wide, res, fill, touch))
                toast("Đã lưu hồ sơ “$nm”")
                buildUi()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ---- Tính năng chính
    private fun featureCard(): View {
        val c = card("Tính năng chính")
        c.addView(row(
            Ui.feature(this, "CỬA SỔ NỔI", "Chỉnh khi đang chơi", FloatingService.running) { toggleFloat() },
            Ui.feature(this, "CHIẾU THẬT", "Lấp đầy, không viền", prefs.fill) { prefs.fill = !prefs.fill; buildUi() },
            Ui.feature(this, "ÁP DỤNG / KHÔI PHỤC", "Quay về bằng 1 chạm", prefs.active) { quickToggle() }
        ))
        return c
    }

    /** Đang kéo giãn -> khôi phục; chưa -> bật lại hồ sơ dùng gần nhất (hoặc cài đặt chỉnh tay). */
    private fun quickToggle() {
        if (prefs.active) { Actions.restore(this, "app") { m -> toast(m); reload() }; return }
        val p = Profiles.get(this, prefs.lastProfile)
        when {
            p != null -> Actions.applyProfile(this, p, "app") { m -> toast(m); reload() }
            prefs.k > 1f -> Actions.applyManual(this, prefs.k, prefs.wide, prefs.res, "app") { m -> toast(m); reload() }
            else -> toast("Hãy chọn một hồ sơ nhanh hoặc kéo hệ số trước")
        }
    }

    private fun toggleFloat() {
        if (FloatingService.running) stopService(Intent(this, FloatingService::class.java)) else startFloat()
        ui.postDelayed({ buildUi() }, 700)
    }

    // ---- Thông tin máy
    private fun deviceCard(d: Dev?): View {
        val c = card("Thông tin thiết bị")
        if (d == null) { c.addView(Ui.tv(this, "Đang đọc thông tin…", 13f, Ui.MUTED)); return c }
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

    // ---- Kéo dãn (chỉnh tay)
    private fun stretchCard(d: Dev?): View {
        bigTv = Ui.tv(this, fmtK(prefs.k), 44f, Ui.RED, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.04f; Ui.glowText(this, Ui.RED, 14f)
        }
        infoTv = Ui.tv(this, "", 13f, Ui.TEXT).apply { setPadding(0, dp(8), 0, 0) }
        seek = SeekBar(this).apply {
            Ui.seek(this)
            max = 50; progress = ((prefs.k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.k = 1f + p / 100f; updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        val c = card("Kéo dãn màn hình (chỉnh tay)", bigTv!!, seek!!)

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
        c.addView(Ui.switchRow(this, "Kéo giãn thật", "Lấp đầy viền đen bằng SurfaceFlinger", prefs.fill) { prefs.fill = !prefs.fill; buildUi() })
        c.addView(Ui.switchRow(this, "Sửa cảm ứng", "Modo Pro · thử nghiệm", prefs.touch) { toggleTouch() })
        c.addView(row(Ui.btn(this, "Chẩn đoán", Ui.GRAY) { showDiag() }))
        c.addView(row(*listOf(100, 90, 80, 70, 60).map { r ->
            Ui.btn(this, "$r%", if (prefs.res == r) Ui.RED else Ui.GRAY) { prefs.res = r; buildUi() }
        }.toTypedArray()))
        c.addView(infoTv!!)
        c.addView(row(
            Ui.btn(this, "Áp dụng") { Actions.applyManual(this, prefs.k, prefs.wide, prefs.res, "app") { m -> toast(m); reload() } },
            Ui.btn(this, "Reset mặc định", Ui.GRAY) { Actions.restore(this, "app") { m -> toast(m); reload() } }
        ))
        c.addView(row(Ui.btn(this, "+ Lưu thành hồ sơ nhanh", Ui.GRAY) { saveCurrent() }))
        c.addView(note("Hàng 100%–60% là tỉ lệ độ phân giải (giảm để nhẹ máy hơn). DPI tự bù để giao diện không đổi kích thước."))
        c.addView(note("“Kéo giãn thật” ép SurfaceFlinger kéo hình ra TOÀN BỘ panel (hết viền đen). Game vẫn thấy màn hình 16:9 nên không bị khóa tỉ lệ. " +
            "Cảm ứng: hệ thống vẫn chấm theo vùng gốc nên 2 cạnh bị chết/lệch. Bật “Sửa cảm ứng” (thử nghiệm, giống Modo Pro của Cat) để app tự phát lại chạm đúng chỗ. " +
            "Kẹt cảm ứng: bấm Volume+ 3 lần liên tiếp để khôi phục khẩn cấp."))
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

    private fun toggleTouch() {
        if (prefs.touch) {
            prefs.touch = false; buildUi()
            Shizu.call({ it.setTouch(false, 0, 0, 0, 0, 0, 0, 0, false, false) }) { toast("Đã tắt sửa cảm ứng") }
            return
        }
        dlg()
            .setTitle("Bật sửa cảm ứng (thử nghiệm)")
            .setMessage("App sẽ giữ thiết bị cảm ứng và tự phát lại mỗi lần chạm đúng vị trí trên hình đã kéo giãn, nhờ đó 2 cạnh vẫn ấn/vuốt được.\n\n" +
                "• Cảm ứng bị kẹt: bấm Volume+ 3 lần liên tiếp để khôi phục khẩn cấp (hoặc giữ nguồn để khởi động lại).\n" +
                "• Chạm được phát lại bằng cơ chế chèn sự kiện; game/anti-cheat có thể coi đó là thao tác lạ — tự cân nhắc rủi ro.\n\n" +
                "Sau khi bật, bấm “Áp dụng” để kích hoạt.")
            .setPositiveButton("Bật") { _, _ -> prefs.touch = true; buildUi() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showDiag() = Engine.diag { text ->
        dlg().setTitle("Chẩn đoán kéo giãn").setMessage(text).setPositiveButton("Đóng", null).show()
    }

    // ---- Game + phiên bảo vệ
    private fun gameCard(): View {
        val games = Games.all(this)
        val c = card("Game · phiên bảo vệ")
        c.addView(Ui.tv(this, "Bấm ▶ Chơi: áp dụng hồ sơ rồi mở game. Khi bạn thoát game, màn hình TỰ về bình thường; quay lại game thì tự kéo giãn lại.", 12f, Ui.MUTED))
        val ses = SessionService.running
        if (ses != null) {
            c.addView(Ui.tv(this, "● Đang có phiên: $ses", 13f, Ui.RED, true).apply { setPadding(0, dp(8), 0, 0) })
            c.addView(row(Ui.btn(this, "Kết thúc & khôi phục") {
                SessionService.stop(this)
                ui.postDelayed({ reload() }, 1500)
            }))
        }
        if (games.isEmpty()) c.addView(note("Chưa có game nào. Bấm “+ Thêm game”."))
        games.forEach { c.addView(gameRow(it)) }
        c.addView(row(Ui.btn(this, "+ Thêm game", Ui.GRAY) { pickGame() }))
        c.addView(note("Nên áp dụng TRƯỚC khi vào game. Đổi độ phân giải giữa trận có thể làm game tải lại hoặc thoát."))
        return c
    }

    private fun gameRow(g: GameEntry): View {
        val pm = packageManager
        val label = try { pm.getApplicationInfo(g.pkg, 0).loadLabel(pm).toString() } catch (_: Exception) { null }
        val icon = try { pm.getApplicationIcon(g.pkg) } catch (_: Exception) { null }
        val prof = Profiles.get(this, g.profile)
        val on = SessionService.running == g.pkg
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
            addView(ImageView(context).apply {
                if (icon != null) setImageDrawable(icon) else setImageResource(R.drawable.ic_logo)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0)
                addView(Ui.tv(context, label ?: g.pkg, 14f, if (label != null) Ui.TEXT else Ui.MUTED, true))
                addView(Ui.tv(context, (if (label == null) "chưa cài · " else "") + "Hồ sơ: " + (prof?.name ?: "cài đặt hiện tại"), 11f, Ui.DIM))
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.btn(context, if (on) "Đang chơi" else "▶ Chơi", if (on) Ui.GRAY else Ui.RED) { startSession(g) })
            addView(Ui.btn(context, "⋯", Ui.GRAY) { gameMenu(g) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
        }
    }

    private fun startSession(g: GameEntry) {
        if (packageManager.getLaunchIntentForPackage(g.pkg) == null) { toast("Game chưa cài: ${g.pkg}"); return }
        if (!Shizu.granted()) { toast("Cần bật Shizuku và cấp quyền cho StretchX"); return }
        val prof = Profiles.get(this, g.profile)
            ?: Profile("", "Hiện tại", prefs.k, prefs.wide, prefs.res, prefs.fill, prefs.touch)
        askNotifPermission()
        Actions.applyProfile(this, prof, "game") { m ->
            if (Actions.failed(m)) { toast(m); reload() }
            else {
                toast(m)
                SessionService.start(this, g.pkg, prof)
                ui.postDelayed({ launchPkg(g.pkg); reload() }, 700)
            }
        }
    }

    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) toast("Không mở được $pkg") else startActivity(i)
    }

    private fun gameMenu(g: GameEntry) {
        dlg().setTitle(g.pkg)
            .setItems(arrayOf("Chọn hồ sơ cho game này", "Xóa khỏi danh sách")) { _, i ->
                when (i) {
                    0 -> chooseGameProfile(g)
                    1 -> { Games.remove(this, g.pkg); buildUi() }
                }
            }
            .show()
    }

    private fun chooseGameProfile(g: GameEntry) {
        val list = Profiles.all(this)
        val names = (listOf("Cài đặt hiện tại (chỉnh tay)") + list.map { "${it.name} — ${it.summary()}" }).toTypedArray()
        dlg().setTitle("Hồ sơ cho game")
            .setItems(names) { _, i -> Games.setProfile(this, g.pkg, if (i == 0) "" else list[i - 1].id); buildUi() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickGame() {
        val pm = packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        dlg().setTitle("Thêm game")
            .setItems(list.map { "${it.second}\n${it.first}" }.toTypedArray()) { _, i -> Games.add(this, list[i].first); buildUi() }
            .show()
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    // ---- Phím tắt nhanh (Quick Settings)
    private fun tileCard(): View {
        val c = card("Phím tắt nhanh")
        c.addView(Ui.tv(this, "Bật/tắt kéo giãn ngay từ bảng phím tắt của máy (vuốt từ cạnh trên xuống) — không cần mở app, không cần kéo tay. Mỗi ô gắn với 1 hồ sơ đã lưu.", 12f, Ui.MUTED))
        for (slot in 0..2) {
            val p = Profiles.forSlot(this, slot)
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                addView(Ui.tv(context, "Ô ${slot + 1}: ${p?.name ?: "—"}", 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.btn(context, "Đổi hồ sơ", Ui.GRAY) { pickTileProfile(slot) })
                addView(Ui.btn(context, "+ Thêm ô", Ui.GRAY) { addTile(slot) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
            })
        }
        c.addView(note("Nếu nút “+ Thêm ô” không hiện hộp xác nhận: vuốt xuống bảng phím tắt → biểu tượng bút (Chỉnh sửa) → " +
            "mục “Ô ứng dụng thứ 3” → giữ và kéo “StretchX 1/2/3” lên khu vực phím tắt. Chạm ô = bật, chạm lại = khôi phục. " +
            "Giữ lâu ô để mở app."))
        return c
    }

    private fun pickTileProfile(slot: Int) {
        val list = Profiles.all(this)
        if (list.isEmpty()) { toast("Hãy tạo hồ sơ trước"); return }
        dlg().setTitle("Hồ sơ cho ô ${slot + 1}")
            .setItems(list.map { "${it.name} — ${it.summary()}" }.toTypedArray()) { _, i -> prefs.setTile(slot, list[i].id); buildUi() }
            .show()
    }

    private fun addTile(slot: Int) {
        val cls = listOf(StretchTile1::class.java, StretchTile2::class.java, StretchTile3::class.java)[slot]
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sbm = getSystemService(android.app.StatusBarManager::class.java)
                val p = Profiles.forSlot(this, slot)
                sbm.requestAddTileService(
                    ComponentName(this, cls), p?.name ?: "StretchX ${slot + 1}",
                    Icon.createWithResource(this, R.drawable.ic_tile), mainExecutor
                ) { r ->
                    toast(when (r) {
                        2 -> "Đã thêm ô vào phím tắt ✔"
                        1 -> "Ô này đã có trong phím tắt"
                        else -> "Chưa thêm — hãy kéo thủ công (xem hướng dẫn bên dưới)"
                    })
                }
                return
            } catch (_: Throwable) {}
        }
        toast("Vuốt xuống bảng phím tắt → biểu tượng bút → kéo “StretchX ${slot + 1}” từ mục “Ô ứng dụng thứ 3” lên trên")
    }

    // ---- Cửa sổ nổi
    private fun floatCard(): View {
        val can = Settings.canDrawOverlays(this)
        val c = card("Cửa sổ nổi (pop-up)", Ui.tv(this, if (can) "Quyền hiển thị trên ứng dụng khác: đã cấp ✔" else "Cần cấp quyền hiển thị trên ứng dụng khác.", 13f, if (can) Ui.OK else Ui.TEXT))
        if (!can) c.addView(row(Ui.btn(this, "Cấp quyền") { grantOverlay() }))
        c.addView(row(
            Ui.btn(this, "Bật cửa sổ nổi") { startFloat(); ui.postDelayed({ buildUi() }, 800) },
            Ui.btn(this, "Tắt", Ui.GRAY) { stopService(Intent(this, FloatingService::class.java)); ui.postDelayed({ buildUi() }, 500) }
        ))
        c.addView(Ui.switchRow(this, "Ẩn/hiện bằng Volume −", "Nhấn đúp Volume − khi đang chơi (cần Shizuku)", prefs.volHide) {
            prefs.volHide = !prefs.volHide
            if (FloatingService.running)
                startService(Intent(this, FloatingService::class.java).setAction(FloatingService.ACTION_KEYS))
            buildUi()
        })
        c.addView(note("Bong bóng nổi kéo thả được: chạm để mở thanh chỉnh 1.00x–1.50x, hàng hồ sơ nhanh và nút Reset. Thông báo có nút “Reset màn hình”, “Ẩn/Hiện” và “Tắt”."))
        return c
    }

    private fun grantOverlay() {
        if (Shizu.granted()) Shizu.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow") { reload() }
        else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startFloat() {
        if (!Settings.canDrawOverlays(this)) { grantOverlay(); toast("Hãy cấp quyền rồi bấm lại"); return }
        askNotifPermission()
        startForegroundService(Intent(this, FloatingService::class.java))
        toast("Đã bật bong bóng nổi")
    }

    // ---- Lịch sử
    private fun historyCard(): View {
        val items = History.all(this).take(8)
        val c = card("Lịch sử")
        if (items.isEmpty()) c.addView(Ui.tv(this, "Chưa có lần áp dụng nào.", 13f, Ui.MUTED))
        else c.addView(Ui.tv(this, "Chạm một dòng để áp dụng lại.", 12f, Ui.MUTED))
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.US)
        items.forEach { hi ->
            val src = when (hi.src) { "tile" -> "Phím tắt"; "float" -> "Nổi"; "game" -> "Game"; else -> "App" }
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(7), 0, dp(7))
                addView(Ui.tv(context, fmt.format(Date(hi.t)), 11f, Ui.DIM), LinearLayout.LayoutParams(dp(78), WRAP))
                addView(Ui.tv(context, if (hi.reset) "Khôi phục" else "${hi.label} · ${fmtK(hi.k)}", 13f, if (hi.reset) Ui.MUTED else Ui.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.tv(context, src, 11f, Ui.DIM))
                isClickable = true
                setOnClickListener { reapply(hi) }
            })
        }
        if (items.isNotEmpty()) c.addView(row(Ui.btn(this, "Xóa lịch sử", Ui.GRAY) { History.clear(this); buildUi() }))
        return c
    }

    private fun reapply(h: HistoryItem) {
        if (h.reset) { Actions.restore(this, "app") { m -> toast(m); reload() }; return }
        prefs.wide = h.wide; prefs.res = h.res
        Actions.applyManual(this, h.k, h.wide, h.res, "app") { m -> toast(m); reload() }
    }
}
