package com.vstretch.app

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Locale

data class Dev(
    val model: String, val android: String, val soc: String, val abi: String, val ramGb: Float,
    val physW: Int, val physH: Int,       // độ phân giải panel, hướng tự nhiên (như `wm size`)
    val curW: Int, val curH: Int,         // độ phân giải đang dùng (có override)
    val physDpi: Int, val curDpi: Int,
    val hz: Float, val modes: String,
    val inches: Float, val aspect: String,
    val overridden: Boolean, val viaShizuku: Boolean, val redmagic: Boolean
)

object DeviceInfo {
    private val rxPS = Regex("Physical size:\\s*(\\d+)x(\\d+)")
    private val rxOS = Regex("Override size:\\s*(\\d+)x(\\d+)")
    private val rxPD = Regex("Physical density:\\s*(\\d+)")
    private val rxOD = Regex("Override density:\\s*(\\d+)")

    fun aspectLabel(w: Int, h: Int): String {
        val a = max(w, h).toFloat() / min(w, h)
        val std = listOf("5:4" to 1.25f, "4:3" to 4f / 3, "3:2" to 1.5f, "16:10" to 1.6f, "16:9" to 16f / 9,
            "2:1" to 2f, "19.5:9" to 19.5f / 9, "20:9" to 20f / 9, "21:9" to 21f / 9)
        val best = std.minByOrNull { abs(it.second - a) }!!
        return if (abs(best.second - a) / a < 0.02f) best.first else String.format(Locale.US, "%.2f:1", a)
    }

    /** wm = đầu ra của `wm size; wm density` (null nếu chưa có Shizuku). */
    fun read(ctx: Context, wm: String?): Dev {
        val disp = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val real = DisplayMetrics()
        @Suppress("DEPRECATION") disp.getRealMetrics(real)
        val mode = disp.mode

        var pw = mode.physicalWidth
        var ph = mode.physicalHeight
        val l = max(real.widthPixels, real.heightPixels)
        val s = min(real.widthPixels, real.heightPixels)
        var cw = if (pw < ph) s else l
        var ch = if (pw < ph) l else s
        var over = max(pw, ph) != l || min(pw, ph) != s
        var dpiPhys = real.densityDpi
        var dpiCur = real.densityDpi

        if (wm != null) {
            rxPS.find(wm)?.let { pw = it.groupValues[1].toInt(); ph = it.groupValues[2].toInt() }
            val os = rxOS.find(wm)
            if (os != null) { cw = os.groupValues[1].toInt(); ch = os.groupValues[2].toInt(); over = true }
            else { cw = pw; ch = ph; over = false }
            rxPD.find(wm)?.let { dpiPhys = it.groupValues[1].toInt(); dpiCur = dpiPhys }
            rxOD.find(wm)?.let { dpiCur = it.groupValues[1].toInt() }
        }

        // DPI vật lý thật (xdpi/ydpi) chỉ lưu khi chưa bị override, để ước lượng inch cho chính xác
        val pr = Prefs(ctx)
        if (!over && real.xdpi > 0f) { pr.xdpi = real.xdpi; pr.ydpi = real.ydpi }
        val avg = if (pr.xdpi > 0f) (pr.xdpi + pr.ydpi) / 2f else dpiPhys.toFloat()
        val inches = hypot(pw.toFloat(), ph.toFloat()) / avg

        val modes = disp.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate.roundToInt() }.distinct().sorted().joinToString("/") + " Hz"

        val mem = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mem)
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        val id = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}".lowercase()

        return Dev(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            soc = soc, abi = Build.SUPPORTED_ABIS.joinToString(", "),
            ramGb = mem.totalMem / (1024f * 1024f * 1024f),
            physW = pw, physH = ph, curW = cw, curH = ch, physDpi = dpiPhys, curDpi = dpiCur,
            hz = disp.refreshRate, modes = modes, inches = inches, aspect = aspectLabel(pw, ph),
            overridden = over, viaShizuku = wm != null,
            redmagic = "redmagic" in id || "nubia" in id || "zte" in id
        )
    }
}
