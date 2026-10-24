package com.vstretch.app

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun fmtK(k: Float): String = String.format(Locale.US, "%.2fx", k)

data class Plan(
    val wmSize: String,      // chuỗi cho `wm size` (theo hướng tự nhiên của máy)
    val vl: Int, val vs: Int, // cạnh dài / ngắn của màn hình ảo (landscape)
    val density: Int,
    val k: Float,            // hệ số thực tế sau khi kẹp theo giới hạn máy
    val barPct: Float,       // % diện tích panel bị viền đen
    val maxK: Float,         // mức kéo tối đa an toàn cho máy này
    val isDefault: Boolean,
    val lnW: Int, val lnH: Int // kích thước màn hình logic theo hướng tự nhiên (đúng thứ tự `wm size`)
) {
    val aspect get() = vl.toFloat() / vs
}

object Stretch {
    const val MAX = 1.50f
    private const val MIN_SHORT = 480   // sàn độ phân giải để không "dãn quá mức"
    private const val MIN_LONG = 800

    private fun even(x: Float) = (x.roundToInt() / 2) * 2

    /**
     * wide  : tỉ lệ ảo = tỉ lệ máy × k  (cạnh dài giữ nguyên, cạnh ngắn chia k)  -> viền trên/dưới
     * !wide : tỉ lệ ảo = tỉ lệ máy ÷ k  (cạnh ngắn giữ nguyên, cạnh dài chia k)  -> viền trái/phải
     * Android luôn "fit" giữ tỉ lệ khi `wm size` khác tỉ lệ panel, nên viền đen là không tránh được;
     * hàm này chọn độ phân giải ảo LỚN NHẤT trong panel để viền chỉ bằng đúng phần bắt buộc.
     */
    private fun virt(L: Int, S: Int, k: Float, wide: Boolean, r: Float): Pair<Int, Int> {
        val l = if (wide) L.toFloat() else L / k
        val s = if (wide) S / k else S.toFloat()
        return even(l * r) to even(s * r)
    }

    fun plan(physW: Int, physH: Int, baseDpi: Int, kReq: Float, wide: Boolean, resPct: Int): Plan {
        val L = max(physW, physH)
        val S = min(physW, physH)
        val r = resPct.coerceIn(50, 100) / 100f
        fun ok(k: Float): Boolean { val (l, s) = virt(L, S, k, wide, r); return l >= MIN_LONG && s >= MIN_SHORT }

        var kc = (kReq.coerceIn(1f, MAX) * 100).roundToInt()
        while (kc > 100 && !ok(kc / 100f)) kc--
        var km = (MAX * 100).roundToInt()
        while (km > 100 && !ok(km / 100f)) km--

        val k = kc / 100f
        val v = virt(L, S, k, wide, r)
        val vl = v.first.coerceIn(2, L)
        val vs = v.second.coerceIn(2, S)
        val scale = min(L.toFloat() / vl, S.toFloat() / vs)           // px vật lý / px ảo
        val bars = 1f - (vl * scale * vs * scale) / (L.toFloat() * S)
        val density = (baseDpi / scale).roundToInt().coerceAtLeast(72) // giữ kích thước UI vật lý
        val size = if (physW < physH) "${vs}x${vl}" else "${vl}x${vs}"
        val portraitNative = physW < physH
        return Plan(size, vl, vs, density, k, bars * 100f, km / 100f, k <= 1f && resPct >= 100,
            if (portraitNative) vs else vl, if (portraitNative) vl else vs)
    }
}
