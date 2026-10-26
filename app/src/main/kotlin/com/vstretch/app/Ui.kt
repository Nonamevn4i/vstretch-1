package com.vstretch.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * THEME "RedMagic Cyber-Red": nền đen carbon, nhấn đỏ #FF2D55 / cam neon #FF5722,
 * góc cắt vát (hard-edge), viền phát sáng đỏ, thiết kế phẳng, phân cấp bằng độ sáng chữ.
 * Chỉ chứa phần HIỂN THỊ — không có logic chức năng.
 */
object Ui {
    // ---- bảng màu ----
    const val BG = 0xFF0A0A0A.toInt()
    const val CARD = 0xFF121215.toInt()
    const val LINE = 0xFF2A2A31.toInt()
    const val RED = 0xFFFF2D55.toInt()
    const val ORANGE = 0xFFFF5722.toInt()
    const val TEXT = 0xFFF4F4F6.toInt()      // cấp 1: sáng nhất
    const val MUTED = 0xFF9A9AA4.toInt()     // cấp 2
    const val DIM = 0xFF5D5D68.toInt()       // cấp 3
    const val OK = 0xFF2EE59D.toInt()
    const val GRAY = 0xFF1C1C21.toInt()      // nút phụ (giữ tên cũ để code cũ vẫn dùng được)

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).roundToInt()

    // ---- tương thích với code cũ ----
    fun round(color: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = r
        if (sw > 0) setStroke(sw, stroke)
    }

    fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    // ---- chữ ----
    fun tv(c: Context, s: CharSequence, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = s; textSize = sp; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

    /** Chữ phát sáng (đổ bóng cùng màu). */
    fun glowText(t: TextView, color: Int, radiusDp: Float = 10f) {
        t.setShadowLayer(radiusDp * t.resources.displayMetrics.density, 0f, 0f, color)
    }

    /** Tiêu đề thẻ: vạch đỏ cắt vát + chữ HOA giãn chữ. */
    fun title(c: Context, s: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(c, 10))
        addView(View(c).apply { background = CutDrawable(RED, cut = dp(c, 2).toFloat(), glow = RED, glowW = dp(c, 2).toFloat()) },
            LinearLayout.LayoutParams(dp(c, 4), dp(c, 16)).apply { rightMargin = dp(c, 8) })
        addView(tv(c, s.uppercase(), 14f, TEXT, true).apply { letterSpacing = 0.12f })
    }

    /** Header ứng dụng: logo + wordmark STRETCHX. */
    fun header(c: Context, sub: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(c, 10), dp(c, 12), dp(c, 10), dp(c, 6))
        addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(dp(c, 56), dp(c, 40)))
        val word = SpannableString("STRETCHX").apply {
            setSpan(ForegroundColorSpan(RED), 7, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 10), 0, 0, 0)
            addView(tv(c, word, 22f, TEXT, true).apply { letterSpacing = 0.1f })
            addView(tv(c, sub, 10f, MUTED, true).apply { letterSpacing = 0.14f })
        })
    }

    // ---- nền ----
    /** Thẻ phẳng: nền đen xám, viền mảnh, cắt vát 2 góc. */
    fun panel(c: Context): Drawable = CutDrawable(CARD, LINE, dp(c, 1).toFloat(), dp(c, 14).toFloat())

    /** Bảng của cửa sổ nổi: bán trong suốt, viền đỏ, phát sáng nhẹ. */
    fun overlayPanel(c: Context): Drawable =
        CutDrawable(0xF2121215.toInt(), 0x99FF2D55.toInt(), dp(c, 1).toFloat(), dp(c, 12).toFloat(), 0x66FF2D55, dp(c, 4).toFloat())

    /** Bong bóng nổi: khối đỏ cắt vát, sáng viền. */
    fun bubbleBg(c: Context): Drawable =
        CutDrawable(0xF0FF2D55.toInt(), 0, 0f, dp(c, 12).toFloat(), 0xFFFFB3C1.toInt(), dp(c, 3).toFloat())

    /** Nền carbon + ánh LED đỏ ở đáy màn hình. */
    fun carbon(c: Context): Drawable {
        val s = dp(c, 5).coerceAtLeast(3)
        val bmp = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(BG)
        val p = Paint().apply { color = 0x0CFFFFFF }
        cv.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        cv.drawRect(s.toFloat(), s.toFloat(), s * 2f, s * 2f, p)
        val tile = BitmapDrawable(c.resources, bmp).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
        val led = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x00000000, 0x26FF2D55))
        return LayerDrawable(arrayOf(tile, led))
    }

    // ---- nút ----
    /**
     * fill == RED  -> nút chính (đỏ, sáng viền). Mọi màu khác -> nút phụ (đen xám, viền mảnh).
     * Các nút "đang chọn" của code cũ truyền RED nên tự thành highlight.
     */
    fun btn(c: Context, s: String, fill: Int = RED, onClick: () -> Unit) = Button(c).apply {
        val primary = fill == RED
        val cut = dp(c, 10).toFloat()
        val line = dp(c, 1).toFloat()
        val normal: Drawable
        val pressed: Drawable
        if (primary) {
            normal = CutDrawable(RED, 0, 0f, cut, 0xFFFF8FA5.toInt(), dp(c, 3).toFloat())
            pressed = CutDrawable(0xFFC71F43.toInt(), 0, 0f, cut)
        } else {
            normal = CutDrawable(GRAY, LINE, line, cut)
            pressed = CutDrawable(0xFF2A2A31.toInt(), RED, line, cut)
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
        stateListAnimator = null
        text = s; isAllCaps = true; textSize = 12f; letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else TEXT)
        minHeight = 0; minimumHeight = dp(c, 40); minWidth = 0; minimumWidth = dp(c, 44)
        setPadding(dp(c, 10), 0, dp(c, 10), 0)
        setOnClickListener { onClick() }
    }

    /** Công tắc kiểu "liquid glass" + nhãn + mô tả. onClick đổi trạng thái ở code gọi. */
    fun switchRow(c: Context, label: String, sub: String, on: Boolean, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(c, 8), 0, dp(c, 2))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(c, label, 14f, if (on) TEXT else MUTED, true))
            addView(tv(c, sub, 11f, DIM))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(GlassSwitch(c, on))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** Thanh trượt: rãnh tối, phần đã kéo đỏ, núm sáng. */
    fun seek(s: SeekBar) {
        s.progressTintList = ColorStateList.valueOf(RED)
        s.progressBackgroundTintList = ColorStateList.valueOf(LINE)
        s.thumbTintList = ColorStateList.valueOf(TEXT)
    }
}

/** Khối phẳng cắt vát (trên-trái & dưới-phải), viền mảnh tuỳ chọn, phát sáng NỘI BỘ (không tràn ra ngoài view). */
class CutDrawable(
    private val fill: Int,
    private val line: Int = 0,
    private val lineW: Float = 0f,
    private val cut: Float,
    private val glow: Int = 0,
    private val glowW: Float = 0f
) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onBoundsChange(b: Rect) {
        val i = lineW / 2f
        val l = b.left + i; val t = b.top + i; val r = b.right - i; val bt = b.bottom - i
        val c = minOf(cut, (r - l) / 2f, (bt - t) / 2f).coerceAtLeast(0f)
        path.reset()
        path.moveTo(l + c, t); path.lineTo(r, t)
        path.lineTo(r, bt - c); path.lineTo(r - c, bt)
        path.lineTo(l, bt); path.lineTo(l, t + c)
        path.close()
    }

    override fun draw(cv: Canvas) {
        p.style = Paint.Style.FILL; p.color = fill
        cv.drawPath(path, p)
        if (glow != 0 && glowW > 0f) {
            cv.save(); cv.clipPath(path)
            p.style = Paint.Style.STROKE
            val a = Color.alpha(glow)
            for ((k, al) in listOf(2.0f to 0.14f, 1.4f to 0.20f, 0.9f to 0.30f, 0.5f to 0.45f)) {
                p.strokeWidth = glowW * 2f * k
                p.color = glow; p.alpha = (a * al).toInt().coerceIn(0, 255)
                cv.drawPath(path, p)
            }
            cv.restore()
        }
        if (lineW > 0f && line != 0) {
            p.style = Paint.Style.STROKE; p.strokeWidth = lineW; p.color = line
            cv.drawPath(path, p)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Công tắc kính lỏng: rãnh mờ có vệt sáng phía trên, núm cầu bóng; BẬT = rãnh ám đỏ + núm phát sáng. */
class GlassSwitch(c: Context, private val on: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((54 * d).toInt(), (30 * d).toInt())

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
        val track = RectF(d, d, w - d, h - d)
        // rãnh kính
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h,
            if (on) intArrayOf(0x66FF2D55, 0x2EFF2D55) else intArrayOf(0x22FFFFFF, 0x0DFFFFFF), null, Shader.TileMode.CLAMP)
        cv.drawRoundRect(track, r, r, p)
        // vệt sáng cong phía trên
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.55f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        cv.save()
        cv.clipPath(Path().apply { addRoundRect(track, r, r, Path.Direction.CW) })
        cv.drawRect(0f, 0f, w, h * 0.55f, p)
        cv.restore()
        p.shader = null
        // viền
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d
        p.color = if (on) 0xCCFF2D55.toInt() else 0x33FFFFFF
        cv.drawRoundRect(track, r, r, p)
        // núm
        val kr = r - 4f * d
        val cx = if (on) w - r else r
        p.style = Paint.Style.FILL
        if (on) {
            p.color = 0x33FF2D55; cv.drawCircle(cx, h / 2f, kr + 5f * d, p)
            p.color = 0x55FF2D55; cv.drawCircle(cx, h / 2f, kr + 2.5f * d, p)
        }
        p.shader = RadialGradient(cx - kr * 0.3f, h / 2f - kr * 0.4f, kr * 1.5f,
            if (on) intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFB3C1.toInt()) else intArrayOf(0xFFEDEDF2.toInt(), 0xFF7E7E88.toInt()),
            null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, h / 2f, kr, p)
        p.shader = null
    }
}
