package com.vstretch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Cửa sổ nổi: bong bóng kéo thả được, chạm để mở bảng chỉnh kéo dãn + nút Reset. */
class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var lp: WindowManager.LayoutParams
    private var root: LinearLayout? = null
    private lateinit var bubble: TextView
    private lateinit var panel: LinearLayout
    private lateinit var valueTv: TextView
    private lateinit var infoTv: TextView
    private lateinit var dirBtn: android.widget.Button
    private lateinit var seek: SeekBar
    private lateinit var chips: LinearLayout
    private var hidden = false
    private var keyCb: IKeyCb.Stub? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()
        Shizu.ensureBound()
        if (Settings.canDrawOverlays(this)) {
            buildOverlay(); running = true
            if (prefs.volHide) registerKeys()
            onState?.invoke()
        } else { toast("Chưa cấp quyền cửa sổ nổi"); stopSelf() }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            ACTION_RESET -> doReset()
            ACTION_STOP -> stopSelf()
            ACTION_TOGGLE -> toggleHidden()
            ACTION_KEYS -> if (prefs.volHide) registerKeys() else unregisterKeys()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        unregisterKeys()
        val hadWindow = root != null
        root?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        root = null
        if (hadWindow) resyncSoon(applicationContext)
        onState?.invoke()
        super.onDestroy()
    }

    /**
     * Thêm/bỏ cửa sổ làm hệ thống tính lại màn hình, có thể làm phép chiếu kéo giãn nháy về kiểu có viền đen
     * trong chốc lát -> áp lại ngay vài lần (không đổi wm size/density).
     */
    private fun resyncSoon(app: android.content.Context) {
        Engine.resync(app)
        val hh = Handler(Looper.getMainLooper())
        hh.postDelayed({ Engine.resync(app) }, 150)
        hh.postDelayed({ Engine.resync(app) }, 450)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("float", "Cửa sổ nổi StretchX", NotificationManager.IMPORTANCE_LOW))
        fun pi(a: String) = PendingIntent.getService(
            this, a.hashCode(), Intent(this, FloatingService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "float")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("StretchX đang chạy")
            .setContentText("Bong bóng nổi đang bật")
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_revert), "Reset màn hình", pi(ACTION_RESET)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_view), "Ẩn/Hiện", pi(ACTION_TOGGLE)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Tắt", pi(ACTION_STOP)).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildOverlay() {
        bubble = Ui.tv(this, fmtK(prefs.k), 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = Ui.bubbleBg(context)
        }
        valueTv = Ui.tv(this, fmtK(prefs.k), 24f, Ui.RED, true).apply { Ui.glowText(this, Ui.RED, 8f) }
        infoTv = Ui.tv(this, "", 11f, Ui.MUTED)

        seek = SeekBar(this).apply {
            Ui.seek(this)
            max = 50; progress = ((prefs.k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) valueTv.text = fmtK(1f + p / 100f) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { applyK(1f + s.progress / 100f) }
            })
        }

        chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }

        fun row(vararg v: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
            v.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }) }
        }

        dirBtn = Ui.btn(this, dirLabel(), Ui.GRAY) {
            prefs.wide = !prefs.wide; dirBtn.text = dirLabel()
            if (prefs.k > 1f) applyK(prefs.k) else syncUi()
        }

        // Bảng gọn: chỉ số + thanh trượt + [−][Reset][+]. Hướng & thông tin nằm trong mục "Chi tiết".
        val extra = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(row(dirBtn))
            addView(infoTv.apply { setPadding(0, dp(8), 0, 0) })
        }
        val more = Ui.tv(this, "CHI TIẾT ▾", 10f, Ui.DIM, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f; setPadding(0, dp(8), 0, dp(2))
            setOnClickListener {
                val show = extra.visibility != View.VISIBLE
                extra.visibility = if (show) View.VISIBLE else View.GONE
                text = if (show) "THU GỌN ▴" else "CHI TIẾT ▾"
                h.postDelayed({ clamp() }, 80)
            }
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = Ui.overlayPanel(this@FloatingService)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val head = LinearLayout(this@FloatingService).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(valueTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.btn(this@FloatingService, "✕", Ui.GRAY) { toggle() })
            }
            addView(head)
            addView(seek)
            addView(chips, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(row(
                Ui.btn(this@FloatingService, "−", Ui.GRAY) { applyK((prefs.k - 0.05f).coerceAtLeast(1f)) },
                Ui.btn(this@FloatingService, "Reset", Ui.RED) { doReset() },
                Ui.btn(this@FloatingService, "+", Ui.GRAY) { applyK((prefs.k + 0.05f).coerceAtMost(Stretch.MAX)) }
            ))
            addView(more)
            addView(extra)
        }

        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bubble, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(panel, LinearLayout.LayoutParams(dp(240), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        root = rootView

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_HARDWARE_ACCELERATED: cửa sổ nổi từ Service mặc định vẽ bằng CPU (software) -> giật; bật GPU cho mượt
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START; x = prefs.bx; y = prefs.by
            windowAnimations = R.style.Cyber_NoAnim
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) + abs(dy) > dp(6)) moved = true
                        if (moved) { lp.x = sx + dx; lp.y = sy + dy; root?.let { wm.updateViewLayout(it, lp) } }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggle() else { clamp(); prefs.bx = lp.x; prefs.by = lp.y }
                }
                return true
            }
        })

        wm.addView(rootView, lp)
        resyncSoon(applicationContext)
        Engine.refresh(this) { syncUi() }
    }

    /** Hàng hồ sơ nhanh (tối đa 4): chạm = bật, chạm lần nữa = khôi phục. */
    private fun refreshChips() {
        if (!::chips.isInitialized) return
        chips.removeAllViews()
        for (p in Profiles.all(this).take(4)) {
            val on = Profiles.isActive(this, p)
            val b = Ui.btn(this, p.name, if (on) Ui.RED else Ui.GRAY) {
                Actions.toggleProfile(this, p, "float") { m ->
                    if (Actions.warn(m)) toast(m)
                    syncUi(); h.postDelayed({ clamp() }, 700)
                }
            }
            b.maxLines = 1
            b.ellipsize = TextUtils.TruncateAt.END
            b.textSize = 11f
            b.setPadding(dp(2), 0, dp(2), 0)
            chips.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
    }

    private fun toggleHidden() {
        hidden = !hidden
        root?.visibility = if (hidden) View.GONE else View.VISIBLE
        if (hidden) toast("Đã ẩn bong bóng — nhấn đúp Volume − hoặc nút Ẩn/Hiện ở thông báo để hiện lại")
    }

    /** Nghe nhấn đúp Volume − (tiến trình shell Shizuku) để ẩn/hiện bong bóng. */
    private fun registerKeys() {
        val cb = object : IKeyCb.Stub() {
            override fun onKey(code: Int) { h.post { toggleHidden() } }
        }
        keyCb = cb
        Shizu.call({ it.setKeyListener(cb) }) { r -> if (r.startsWith("ERR")) toast("Volume −: " + r.removePrefix("ERR: ")) }
    }

    private fun unregisterKeys() {
        if (keyCb == null) return
        keyCb = null
        Shizu.call({ it.setKeyListener(null) }) {}
    }

    private fun dirLabel() = if (prefs.wide) "Hướng: Rộng" else "Hướng: Hẹp"

    private fun toggle() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (panel.visibility == View.VISIBLE) Engine.refresh(this) { syncUi() }
        h.postDelayed({ clamp() }, 80)
    }

    private fun clamp() {
        val r = root ?: return
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        lp.x = lp.x.coerceIn(0, max(0, dm.widthPixels - r.width))
        lp.y = lp.y.coerceIn(0, max(0, dm.heightPixels - r.height))
        try { wm.updateViewLayout(r, lp) } catch (_: Throwable) {}
    }

    private fun syncUi() {
        if (root == null) return
        bubble.text = fmtK(prefs.k)
        valueTv.text = fmtK(prefs.k)
        seek.progress = ((prefs.k - 1f) * 100).roundToInt()
        dirBtn.text = dirLabel()
        refreshChips()
        val p = Engine.planFor(this, prefs.k, prefs.wide, prefs.res)
        infoTv.text = if (p == null) "Chưa đọc được thông tin máy"
        else "${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)}) · " +
            (if (prefs.fill && !p.isDefault) "lấp đầy ${fmtK(p.k)}" else "viền ≈${"%.0f".format(p.barPct)}%") +
            " · tối đa ${fmtK(p.maxK)}"
    }

    private fun applyK(k: Float) {
        prefs.k = k
        Actions.applyManual(this, k, prefs.wide, prefs.res, "float") { m ->
            if (m.startsWith("ERR") || m.startsWith("Cần") || m.contains("⚠")) toast(m)
            syncUi(); h.postDelayed({ clamp() }, 700)
        }
    }

    private fun doReset() {
        Actions.restore(this, "float") { m -> toast(m); syncUi(); h.postDelayed({ clamp() }, 700) }
    }

    companion object {
        const val ACTION_RESET = "com.vstretch.app.RESET"
        const val ACTION_STOP = "com.vstretch.app.STOP"
        const val ACTION_TOGGLE = "com.vstretch.app.TOGGLE"
        const val ACTION_KEYS = "com.vstretch.app.KEYS"

        /** Bong bóng nổi đang chạy? (để giao diện hiện đúng trạng thái) */
        @Volatile var running = false

        /** Gọi (main thread) mỗi khi cửa sổ nổi bật/tắt -> giao diện cập nhật đúng 1 lần, không cần hẹn giờ. */
        @Volatile var onState: (() -> Unit)? = null
    }
}
