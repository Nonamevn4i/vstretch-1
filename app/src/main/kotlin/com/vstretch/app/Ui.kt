package com.vstretch.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.TextView
import kotlin.math.roundToInt

/** Helper dựng UI bằng code (không cần XML layout). */
object Ui {
    const val BG = 0xFF0F1923.toInt()
    const val CARD = 0xFF1B2733.toInt()
    const val RED = 0xFFFF4655.toInt()
    const val TEXT = 0xFFECE8E1.toInt()
    const val MUTED = 0xFF8B97A3.toInt()
    const val OK = 0xFF3DDC84.toInt()
    const val GRAY = 0xFF334455.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).roundToInt()

    fun round(color: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = r
        if (sw > 0) setStroke(sw, stroke)
    }

    fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun tv(c: Context, s: CharSequence, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = s; textSize = sp; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun btn(c: Context, s: String, fill: Int = RED, onClick: () -> Unit) = Button(c).apply {
        text = s; isAllCaps = false; textSize = 13f; setTextColor(Color.WHITE)
        background = round(fill, dp(c, 10).toFloat())
        minHeight = 0; minimumHeight = dp(c, 40); minWidth = 0; minimumWidth = dp(c, 44)
        setPadding(dp(c, 10), 0, dp(c, 10), 0)
        setOnClickListener { onClick() }
    }
}
