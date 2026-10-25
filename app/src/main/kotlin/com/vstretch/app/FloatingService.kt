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
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startFg()
        Shizu.ensureBound()
        if (Settings.canDrawOverlays(this)) buildOverlay() else { toast("Chưa cấp quyền cửa sổ nổi"); stopSelf() }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            ACTION_RESET -> doReset()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        root?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        root = null
        super.onDestroy()
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
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentTitle("StretchX đang chạy")
            .setContentText("Bong bóng nổi đang bật")
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_revert), "Reset màn hình", pi(ACTION_RESET)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Tắt", pi(ACTION_STOP)).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun edgeGestureInset() = dp(64)

    private fun buildOverlay() {
        bubble = Ui.tv(this, fmtK(prefs.k), 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = Ui.oval(0xEEFF4655.toInt())
        }
        valueTv = Ui.tv(this, fmtK(prefs.k), 22f, Ui.RED, true)
        infoTv = Ui.tv(this, "", 11f, Ui.MUTED)

        seek = SeekBar(this).apply {
            max = 50; progress = ((prefs.k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) valueTv.text = fmtK(1f + p / 100f) }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) { applyK(1f + s.progress / 100f) }
            })
        }

        fun row(vararg v: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
            v.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), 0, dp(2), 0) }) }
        }

        dirBtn = Ui.btn(this, dirLabel(), Ui.GRAY) {
            if (prefs.fill) { toast("Kéo giãn thật luôn dùng hướng Rộng (để 2 cạnh trái/phải nhận chạm). Tắt Kéo giãn thật trong app nếu muốn dùng Hẹp.") }
            else { prefs.wide = !prefs.wide; dirBtn.text = dirLabel(); if (prefs.k > 1f) applyK(prefs.k) else syncUi() }
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            background = Ui.round(0xF2101820.toInt(), dp(16).toFloat(), 0x44FFFFFF, dp(1))
            setPadding(dp(14), dp(10), dp(14), dp(12))
            val head = LinearLayout(this@FloatingService).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(Ui.tv(this@FloatingService, "StretchX", 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(Ui.btn(this@FloatingService, "Thu nhỏ", Ui.GRAY) { toggle() })
            }
            addView(head)
            addView(valueTv)
            addView(seek)
            addView(row(
                Ui.btn(this@FloatingService, "−0.05", Ui.GRAY) { applyK((prefs.k - 0.05f).coerceAtLeast(1f)) },
                Ui.btn(this@FloatingService, "+0.05", Ui.GRAY) { applyK((prefs.k + 0.05f).coerceAtMost(Stretch.MAX)) }
            ))
            addView(row(dirBtn, Ui.btn(this@FloatingService, "Reset", Ui.RED) { doReset() }))
            addView(infoTv.apply { setPadding(0, dp(8), 0, 0) })
        }

        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bubble, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(panel, LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }

        root = rootView

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = prefs.bx; y = prefs.by }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) + abs(dy) > dp(6)) moved = true
                        if (moved) { lp.x = sx + dx; lp.y = sy + dy; clamp() }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggle() else { clamp(); prefs.bx = lp.x; prefs.by = lp.y }
                }
                return true
            }
        })

        wm.addView(rootView, lp)
        h.post {
            clamp()
            prefs.bx = lp.x
        }
        Engine.refresh(this) { syncUi() }
    }

    private fun dirLabel() = if (prefs.effWide) "Hướng: Rộng" else "Hướng: Hẹp"

    private fun toggle() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        clamp()
        if (panel.visibility == View.VISIBLE) Engine.refresh(this) { syncUi() }
        h.postDelayed({ clamp() }, 80)
    }

    private fun clamp() {
        val r = root ?: return
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        val occupiedWidth = if (panel.visibility == View.VISIBLE) max(r.width, dp(260)) else r.width
        val maxX = max(0, dm.widthPixels - occupiedWidth)
        val inset = minOf(edgeGestureInset(), maxX / 2)
        lp.x = lp.x.coerceIn(inset, max(inset, maxX - inset))
        lp.y = lp.y.coerceIn(0, max(0, dm.heightPixels - r.height))
        try { wm.updateViewLayout(r, lp) } catch (_: Throwable) {}
    }

    private fun syncUi() {
        if (root == null) return
        bubble.text = fmtK(prefs.k)
        valueTv.text = fmtK(prefs.k)
        seek.progress = ((prefs.k - 1f) * 100).roundToInt()
        dirBtn.text = dirLabel()
        val p = Engine.planFor(this, prefs.k, prefs.effWide, prefs.res)
        infoTv.text = if (p == null) "Chưa đọc được thông tin máy"
        else "${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)}) · " +
            (if (prefs.fill && !p.isDefault) "lấp đầy ${fmtK(p.k)}, chết cảm ứng trên/dưới ≈${Stretch.deadPx(Engine.dev?.physW ?: p.vl, Engine.dev?.physH ?: p.vs, p.k, true)}px"
            else "viền ≈${"%.0f".format(p.barPct)}%") +
            " · tối đa ${fmtK(p.maxK)}"
    }

    private fun applyK(k: Float) {
        prefs.k = k
        Engine.apply(this, k, prefs.effWide, prefs.res) { m ->
            if (m.startsWith("ERR") || m.startsWith("Cần") || m.contains("⚠")) toast(m)
            syncUi(); h.postDelayed({ clamp() }, 700)
        }
    }

    private fun doReset() {
        Engine.reset(this) { m -> toast(m); syncUi(); h.postDelayed({ clamp() }, 700) }
    }

    companion object {
        const val ACTION_RESET = "com.vstretch.app.RESET"
        const val ACTION_STOP = "com.vstretch.app.STOP"
    }
}
