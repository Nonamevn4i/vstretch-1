package com.vstretch.app

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * HỒ SƠ KÉO GIÃN: lưu sẵn (hệ số, hướng, % độ phân giải, kéo giãn thật, sửa cảm ứng).
 * Bật hồ sơ = áp dụng đúng các giá trị đó, không cần kéo thanh trượt bằng tay.
 * [id] rỗng = hồ sơ tạm (lấy từ cài đặt hiện tại, không lưu).
 */
data class Profile(
    val id: String,
    val name: String,
    val k: Float,
    val wide: Boolean = true,
    val res: Int = 100,
    val fill: Boolean = true,
    val touch: Boolean = false
) {
    fun summary(): String = fmtK(k) + " · " + (if (wide) "Rộng" else "Hẹp") +
        (if (res < 100) " · $res%" else "") + (if (!fill) " · có viền" else "") + (if (touch) " · cảm ứng" else "")
}

/** Một game trong danh sách; [profile] rỗng = dùng cài đặt đang chỉnh. */
data class GameEntry(val pkg: String, val profile: String = "")

data class HistoryItem(
    val t: Long, val label: String, val k: Float, val wide: Boolean, val res: Int,
    val src: String, val reset: Boolean
)

object Profiles {
    private fun toJson(p: Profile) = JSONObject()
        .put("id", p.id).put("name", p.name).put("k", p.k.toDouble())
        .put("wide", p.wide).put("res", p.res).put("fill", p.fill).put("touch", p.touch)

    private fun fromJson(o: JSONObject) = Profile(
        o.getString("id"), o.optString("name", "?"), o.optDouble("k", 1.0).toFloat(),
        o.optBoolean("wide", true), o.optInt("res", 100), o.optBoolean("fill", true), o.optBoolean("touch", false)
    )

    /** Hồ sơ mẫu lần đầu: 1.05x / 1.10x / 1.11x / 1.15x (sửa hoặc xoá thoải mái). */
    private fun seed(): List<Profile> =
        listOf(1.05f, 1.10f, 1.11f, 1.15f).map { Profile("d" + (it * 100).roundToInt(), fmtK(it), it) }

    private fun store(ctx: Context, list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(toJson(it)) }
        Prefs(ctx).profilesJson = a.toString()
    }

    fun all(ctx: Context): List<Profile> {
        val raw = Prefs(ctx).profilesJson
        if (raw.isEmpty()) { val s = seed(); store(ctx, s); return s }
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
        } catch (_: Throwable) {
            val s = seed(); store(ctx, s); s
        }
    }

    fun get(ctx: Context, id: String): Profile? = if (id.isEmpty()) null else all(ctx).firstOrNull { it.id == id }

    fun newId(): String = "p" + java.lang.Long.toString(System.currentTimeMillis(), 36)

    /** Thêm mới hoặc ghi đè (theo id). */
    fun save(ctx: Context, p: Profile) {
        val list = all(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) list[i] = p else list.add(p)
        store(ctx, list)
    }

    fun delete(ctx: Context, id: String) {
        store(ctx, all(ctx).filter { it.id != id })
        val pr = Prefs(ctx)
        for (s in 0..2) if (pr.tile(s) == id) pr.setTile(s, "")
        if (pr.lastProfile == id) pr.lastProfile = ""
        if (pr.activeProfile == id) pr.activeProfile = ""
    }

    /** Hồ sơ của ô phím tắt [slot]; chưa gán thì lấy mặc định (1.10x / 1.05x / 1.15x) hoặc hồ sơ đầu tiên. */
    fun forSlot(ctx: Context, slot: Int): Profile? {
        val list = all(ctx)
        if (list.isEmpty()) return null
        val id = Prefs(ctx).tile(slot)
        list.firstOrNull { it.id == id }?.let { return it }
        val s = slot.coerceIn(0, 2)
        val pref = listOf("d110", "d105", "d115")[s]
        return list.firstOrNull { it.id == pref } ?: list[s.coerceAtMost(list.size - 1)]
    }

    fun isActive(ctx: Context, p: Profile): Boolean {
        val pr = Prefs(ctx)
        return pr.active && p.id.isNotEmpty() && pr.activeProfile == p.id
    }
}

/** Danh sách game của phiên bảo vệ. */
object Games {
    private val KNOWN = listOf(
        Prefs.GAME_VALORANT_CN, "com.tencent.ig", "com.dts.freefiremax",
        "com.activision.callofduty.shooter", "com.miHoYo.GenshinImpact", "com.riotgames.league.wildrift"
    )

    fun installed(pm: PackageManager, pkg: String): Boolean =
        try { pm.getApplicationInfo(pkg, 0); true } catch (_: Throwable) { false }

    private fun store(ctx: Context, list: List<GameEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("pkg", it.pkg).put("profile", it.profile)) }
        Prefs(ctx).gamesJson = a.toString()
    }

    fun all(ctx: Context): List<GameEntry> {
        val pr = Prefs(ctx)
        if (pr.gamesJson.isEmpty()) {
            val pm = ctx.packageManager
            val pkgs = (KNOWN.filter { it == Prefs.GAME_VALORANT_CN || installed(pm, it) } + pr.game).distinct()
            val list = pkgs.map { GameEntry(it) }
            store(ctx, list)
            return list
        }
        return try {
            val a = JSONArray(pr.gamesJson)
            (0 until a.length()).map { val o = a.getJSONObject(it); GameEntry(o.getString("pkg"), o.optString("profile", "")) }
        } catch (_: Throwable) { emptyList() }
    }

    fun add(ctx: Context, pkg: String) {
        val list = all(ctx)
        if (list.none { it.pkg == pkg }) store(ctx, list + GameEntry(pkg))
    }

    fun remove(ctx: Context, pkg: String) = store(ctx, all(ctx).filter { it.pkg != pkg })

    fun setProfile(ctx: Context, pkg: String, profileId: String) =
        store(ctx, all(ctx).map { if (it.pkg == pkg) it.copy(profile = profileId) else it })
}

/** Lịch sử các lần áp dụng / khôi phục (tối đa 30). */
object History {
    private const val MAX = 30

    fun all(ctx: Context): List<HistoryItem> {
        val raw = Prefs(ctx).historyJson
        if (raw.isEmpty()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                HistoryItem(
                    o.optLong("t"), o.optString("n"), o.optDouble("k", 1.0).toFloat(),
                    o.optBoolean("w", true), o.optInt("r", 100), o.optString("s"), o.optBoolean("x", false)
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    fun add(ctx: Context, label: String, k: Float, wide: Boolean, res: Int, src: String, reset: Boolean = false) {
        val list = (listOf(HistoryItem(System.currentTimeMillis(), label, k, wide, res, src, reset)) + all(ctx)).take(MAX)
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("t", it.t).put("n", it.label).put("k", it.k.toDouble())
                .put("w", it.wide).put("r", it.res).put("s", it.src).put("x", it.reset))
        }
        Prefs(ctx).historyJson = a.toString()
    }

    fun clear(ctx: Context) { Prefs(ctx).historyJson = "" }
}

/**
 * Các thao tác cấp cao dùng chung cho app, phím tắt nhanh, cửa sổ nổi và phiên game:
 * gọi Engine rồi ghi nhớ hồ sơ đang dùng + lịch sử. Callback luôn ở main thread.
 */
object Actions {
    fun failed(m: String) = m.startsWith("ERR") || m.startsWith("Cần")
    fun warn(m: String) = failed(m) || m.contains("⚠")

    /** Bật một hồ sơ. */
    fun applyProfile(ctx: Context, p: Profile, src: String, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val pr = Prefs(app)
        pr.fill = p.fill; pr.touch = p.touch; pr.wide = p.wide; pr.res = p.res
        Engine.apply(app, p.k, p.wide, p.res) { m ->
            if (!failed(m)) {
                pr.activeProfile = p.id
                if (p.id.isNotEmpty()) pr.lastProfile = p.id
                History.add(app, p.name, pr.k, p.wide, p.res, src)
            }
            cb(m)
        }
    }

    /** Áp dụng giá trị chỉnh tay (thanh trượt) — bỏ liên kết với hồ sơ. */
    fun applyManual(ctx: Context, k: Float, wide: Boolean, res: Int, src: String, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        val pr = Prefs(app)
        pr.activeProfile = ""
        Engine.apply(app, k, wide, res) { m ->
            if (!failed(m)) History.add(app, fmtK(pr.k), pr.k, wide, res, src)
            cb(m)
        }
    }

    fun restore(ctx: Context, src: String, cb: (String) -> Unit) {
        val app = ctx.applicationContext
        Engine.reset(app) { m ->
            if (!failed(m)) {
                Prefs(app).activeProfile = ""
                History.add(app, "Khôi phục", 1f, true, 100, src, reset = true)
            }
            cb(m)
        }
    }

    /** Hồ sơ đang bật -> khôi phục; ngược lại -> bật hồ sơ. */
    fun toggleProfile(ctx: Context, p: Profile, src: String, cb: (String) -> Unit) {
        if (Profiles.isActive(ctx, p)) restore(ctx, src, cb) else applyProfile(ctx, p, src, cb)
    }
}
