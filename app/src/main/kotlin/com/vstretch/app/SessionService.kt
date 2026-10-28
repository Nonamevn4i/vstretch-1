package com.vstretch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

/**
 * PHIÊN GAME BẢO VỆ: mở game với hồ sơ đã chọn; khi bạn thoát game, màn hình TỰ trở về bình thường,
 * quay lại game thì tự kéo giãn lại. Phiên kết thúc khi game bị tắt hẳn hoặc bấm "Kết thúc & khôi phục".
 *
 * Theo dõi game bằng lệnh shell (qua Shizuku) mỗi ~2 giây: activity đang hiện + pid của game.
 * Nếu không đọc được activity đang hiện thì chỉ dựa vào pid (game còn chạy = coi như đang chơi).
 */
class SessionService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var pkg = ""
    private var label = ""
    private var prof = Profile("", "Hiện tại", 1f)
    private var state = ST_WAIT
    private var busy = false
    private var started = false
    private var miss = 0
    private var errs = 0
    private var startedAt = 0L
    private val tick = Runnable { poll() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i == null) { stopSelf(); return START_NOT_STICKY }
        if (i.action == ACTION_STOP) { end(true); return START_NOT_STICKY }
        val pk = i.getStringExtra(EXTRA_PKG)
        if (pk == null) { stopSelf(); return START_NOT_STICKY }
        h.removeCallbacks(tick)
        pkg = pk
        prof = Profile(
            i.getStringExtra("id") ?: "", i.getStringExtra("name") ?: "Hiện tại",
            i.getFloatExtra("k", 1f), i.getBooleanExtra("wide", true), i.getIntExtra("res", 100),
            i.getBooleanExtra("fill", true), i.getBooleanExtra("touch", false)
        )
        label = try { packageManager.getApplicationInfo(pk, 0).loadLabel(packageManager).toString() } catch (_: Throwable) { pk }
        state = ST_WAIT; miss = 0; errs = 0; busy = false; started = true
        startedAt = SystemClock.elapsedRealtime()
        running = pk
        startFg()
        h.postDelayed(tick, 3000)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        started = false
        running = null
        super.onDestroy()
    }

    private fun poll() {
        if (!started) return
        if (busy) { h.postDelayed(tick, POLL_MS); return }
        val cmd = "dumpsys activity activities 2>/dev/null | grep ResumedActivity | head -n 4; " +
            "echo PID=\$(pidof $pkg 2>/dev/null | head -c 12)"
        Shizu.exec(cmd) { out ->
            if (started) {
                onPoll(out)
                if (started) h.postDelayed(tick, POLL_MS)
            }
        }
    }

    private fun onPoll(out: String) {
        if (out.startsWith("ERR")) { if (++errs >= 5) end(true); return }
        errs = 0
        val i = out.lastIndexOf("PID=")
        val head = if (i >= 0) out.substring(0, i) else out
        val pid = if (i >= 0) out.substring(i + 4).trim() else ""
        val alive = pid.isNotEmpty() && pid[0].isDigit()
        // Đọc được activity đang hiện -> dùng nó; không đọc được -> dựa vào việc game còn chạy
        val fg = if (head.contains("ResumedActivity")) head.contains("$pkg/") else alive
        when (state) {
            ST_WAIT -> {
                if (fg) { state = ST_IN; miss = 0; notifyState() }
                else if (SystemClock.elapsedRealtime() - startedAt > WAIT_MS) end(true)
            }
            ST_IN -> {
                if (!alive) end(true)
                else if (!fg) { if (++miss >= MISS_MAX) leave() }
                else miss = 0
            }
            ST_OUT -> {
                if (!alive) end(false)
                else if (fg) comeBack()
            }
        }
    }

    /** Rời game quá ~6 giây -> trả màn hình về bình thường. */
    private fun leave() {
        busy = true
        Actions.restore(this, "game") { _ -> busy = false; state = ST_OUT; notifyState() }
    }

    /** Quay lại game -> kéo giãn lại. */
    private fun comeBack() {
        busy = true
        Actions.applyProfile(this, prof, "game") { _ -> busy = false; state = ST_IN; miss = 0; notifyState() }
    }

    private fun end(restore: Boolean) {
        if (!started) { stopSelf(); return }
        started = false
        h.removeCallbacks(tick)
        if (restore && Prefs(this).active) Actions.restore(this, "game") { _ -> finish() } else finish()
    }

    private fun finish() {
        running = null
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE) } catch (_: Throwable) {}
        stopSelf()
    }

    // ---------------------------------------------------------------- thông báo

    private fun text() = when (state) {
        ST_WAIT -> "Đang chờ vào $label…"
        ST_IN -> "Đang chơi $label · màn hình đã kéo giãn (${prof.name})"
        else -> "Đã rời $label · màn hình bình thường (tự bật lại khi quay về game)"
    }

    private fun build(): Notification {
        val stop = PendingIntent.getService(
            this, 11, Intent(this, SessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, "session")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("StretchX · phiên game")
            .setContentText(text())
            .setStyle(Notification.BigTextStyle().bigText(text()))
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_revert), "Kết thúc & khôi phục", stop).build())
            .setOngoing(true)
            .build()
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("session", "Phiên game StretchX", NotificationManager.IMPORTANCE_LOW))
        if (Build.VERSION.SDK_INT >= 29) startForeground(2, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2, build())
    }

    private fun notifyState() {
        try { getSystemService(NotificationManager::class.java).notify(2, build()) } catch (_: Throwable) {}
    }

    companion object {
        const val ACTION_STOP = "com.vstretch.app.SESSION_STOP"
        const val EXTRA_PKG = "pkg"
        private const val ST_WAIT = 0
        private const val ST_IN = 1
        private const val ST_OUT = 2
        private const val POLL_MS = 3000L       // dumpsys khá nặng -> thưa hơn để không gây khựng khi đang chơi
        private const val WAIT_MS = 60_000L     // chờ game lên hình tối đa 60 giây
        private const val MISS_MAX = 2          // 2 lần liên tiếp không thấy game (~6 giây) mới coi là đã rời

        /** Gói game đang có phiên (null = không có). */
        @Volatile var running: String? = null

        fun start(ctx: Context, pkg: String, p: Profile) {
            val i = Intent(ctx, SessionService::class.java)
                .putExtra(EXTRA_PKG, pkg).putExtra("id", p.id).putExtra("name", p.name)
                .putExtra("k", p.k).putExtra("wide", p.wide).putExtra("res", p.res)
                .putExtra("fill", p.fill).putExtra("touch", p.touch)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, SessionService::class.java).setAction(ACTION_STOP))
        }
    }
}
