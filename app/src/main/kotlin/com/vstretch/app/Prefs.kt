package com.vstretch.app

import android.content.Context

class Prefs(c: Context) {
    private val sp = c.applicationContext.getSharedPreferences("stretchx", Context.MODE_PRIVATE)

    /** hệ số kéo dãn 1.00..1.50 */
    var k: Float get() = sp.getFloat("k", 1f); set(v) = sp.edit().putFloat("k", v).apply()
    /** true = màn hình ảo RỘNG hơn tỉ lệ máy (viền trên/dưới); false = HẸP hơn (viền trái/phải) */
    var wide: Boolean get() = sp.getBoolean("wide", true); set(v) = sp.edit().putBoolean("wide", v).apply()
    /** % độ phân giải (50..100) */
    var res: Int get() = sp.getInt("res", 100); set(v) = sp.edit().putInt("res", v).apply()
    var game: String get() = sp.getString("game", GAME_VALORANT_CN)!!; set(v) = sp.edit().putString("game", v).apply()
    /** app đang giữ override wm size/density */
    var active: Boolean get() = sp.getBoolean("active", false); set(v) = sp.edit().putBoolean("active", v).apply()
    /** DPI gốc trước khi app can thiệp */
    var baseDpi: Int get() = sp.getInt("baseDpi", 0); set(v) = sp.edit().putInt("baseDpi", v).apply()
    var bx: Int get() = sp.getInt("bx", 40); set(v) = sp.edit().putInt("bx", v).apply()
    var by: Int get() = sp.getInt("by", 240); set(v) = sp.edit().putInt("by", v).apply()
    var xdpi: Float get() = sp.getFloat("xdpi", 0f); set(v) = sp.edit().putFloat("xdpi", v).apply()
    var ydpi: Float get() = sp.getFloat("ydpi", 0f); set(v) = sp.edit().putFloat("ydpi", v).apply()

    companion object {
        /** Package Valorant Mobile bản Trung Quốc (无畏契约:源能行动) — nguồn: trang tải bên thứ ba, có thể đổi trong app */
        const val GAME_VALORANT_CN = "com.tencent.tmgp.codev"
    }
}
