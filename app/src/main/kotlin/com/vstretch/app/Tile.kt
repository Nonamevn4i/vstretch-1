package com.vstretch.app

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * Ô PHÍM TẮT NHANH (Quick Settings). Mỗi ô gắn với 1 hồ sơ kéo giãn đã lưu:
 * chạm = bật hồ sơ (sáng), chạm lần nữa = khôi phục màn hình. Không cần mở app, không cần kéo tay.
 * Có 3 ô (StretchX 1/2/3); chọn hồ sơ cho từng ô trong app (mục "Phím tắt nhanh").
 */
abstract class BaseTile(private val slot: Int) : TileService() {
    private val h = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    override fun onStartListening() {
        super.onStartListening()
        Shizu.ensureBound()
        render()
        if (Shizu.granted()) Engine.refresh(this) { render() }   // đồng bộ lại nếu máy đã bị reset từ nơi khác
    }

    override fun onTileAdded() { super.onTileAdded(); render() }

    override fun onClick() {
        super.onClick()
        if (busy) return
        val p = Profiles.forSlot(this, slot)
        if (p == null) { openApp(); return }
        waitShizuku(6) { ready ->
            if (!ready) {
                toast("Cần bật Shizuku và cấp quyền cho StretchX")
                openApp()
            } else {
                busy = true; render()
                Actions.toggleProfile(this, p, "tile") { m ->
                    busy = false; render()
                    if (Actions.warn(m)) toast(m.lineSequence().firstOrNull { it.isNotBlank() } ?: m)
                }
            }
        }
    }

    /** Vừa bị đánh thức bởi ô: binder Shizuku có thể chưa kịp về -> chờ tối đa ~1.8 giây. */
    private fun waitShizuku(tries: Int, done: (Boolean) -> Unit) {
        if (Shizu.granted()) { done(true); return }
        if (tries <= 0) { done(false); return }
        h.postDelayed({ waitShizuku(tries - 1, done) }, 300)
    }

    private fun render() {
        val t = qsTile ?: return
        val p = Profiles.forSlot(this, slot)
        val on = p != null && Profiles.isActive(this, p)
        t.label = p?.name ?: "StretchX"
        t.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val sub = when {
            busy -> "Đang áp dụng…"
            p == null -> "Chưa có hồ sơ"
            !Shizu.granted() -> "Cần Shizuku"
            on -> "StretchX · Đang bật"
            else -> "StretchX · Tắt"
        }
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = sub
        t.updateTile()
    }

    private fun toast(s: String) = Toast.makeText(applicationContext, s, Toast.LENGTH_SHORT).show()

    private fun openApp() {
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(i)
            }
        } catch (_: Throwable) {}
    }
}

class StretchTile1 : BaseTile(0)
class StretchTile2 : BaseTile(1)
class StretchTile3 : BaseTile(2)
