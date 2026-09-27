package com.day008.neonsurvivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Lightweight, code-drawn ambient motion. No image assets or animation lifecycle to manage. */
class MenuBackdrop(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = 0xFF00D9E8.toInt()
    private val pink = 0xFFFF3D8C.toInt()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = SystemClock.uptimeMillis() / 1000f
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xFF0B1020.toInt(), 0xFF17152E.toInt(), 0xFF09131D.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density * .8f
        paint.color = 0x302C6F82
        val step = 38f * resources.displayMetrics.density
        val drift = (t * 8f * resources.displayMetrics.density) % step
        var y = drift
        while (y < h) { canvas.drawLine(0f, y, w, y, paint); y += step }
        var x = 0f
        while (x < w) { canvas.drawLine(x, 0f, x, h, paint); x += step }
        paint.style = Paint.Style.FILL
        for (i in 0 until 28) {
            val seed = i * 43.73f
            val sx = (sin(seed) * 0.5f + .5f) * w
            val sy = ((cos(seed * 1.7f) * .5f + .5f) * h - t * (6f + i % 5 * 3f) * resources.displayMetrics.density) % h
            val wrapped = if (sy < 0f) sy + h else sy
            val shimmer = (.35f + .25f * sin(t * 2f + seed)).coerceIn(.1f, .65f)
            paint.color = if (i % 4 == 0) pink else cyan
            paint.alpha = (shimmer * 255).toInt()
            canvas.drawCircle(sx, wrapped, (1.2f + i % 3) * resources.displayMetrics.density, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = resources.displayMetrics.density
        paint.color = 0x334CC5D3
        val radius = w * .72f
        canvas.drawCircle(w * .75f, h * .32f, radius + sin(t * .6f) * 12f, paint)
        canvas.drawCircle(w * .75f, h * .32f, radius * .77f + sin(t * .8f) * 15f, paint)
        paint.style = Paint.Style.FILL
        postInvalidateDelayed(33L)
    }
}

class HeroView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyan = 0xFF00D9E8.toInt()
    private val pink = 0xFFFF3D8C.toInt()

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() / 1000f
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f
        val density = resources.displayMetrics.density
        paint.color = 0xC9141D36.toInt(); paint.style = Paint.Style.FILL
        canvas.drawRoundRect(0f, 0f, w, h, 28f * density, 28f * density, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density
        paint.color = 0x5553BDD0
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 28f * density, 28f * density, paint)
        canvas.save()
        canvas.translate(cx, cy)
        val r = minOf(w, h) * .33f
        paint.strokeWidth = 4f * density
        paint.color = cyan
        canvas.drawArc(-r, -r, r, r, t * 23f, 120f, false, paint)
        paint.color = pink
        canvas.drawArc(-r, -r, r, r, -t * 31f + 160f, 84f, false, paint)
        paint.strokeWidth = 1.5f * density
        paint.color = 0x6680E6E8
        canvas.drawCircle(0f, 0f, r * .76f + sin(t * 2f) * 3f * density, paint)
        canvas.rotate(sin(t * .9f) * 10f)
        val core = r * (.47f + sin(t * 3f) * .025f)
        paint.style = Paint.Style.FILL; paint.color = pink
        val star = Path().apply {
            moveTo(0f, -core)
            lineTo(core * .3f, -core * .25f)
            lineTo(core, 0f)
            lineTo(core * .3f, core * .25f)
            lineTo(0f, core)
            lineTo(-core * .3f, core * .25f)
            lineTo(-core, 0f)
            lineTo(-core * .3f, -core * .25f)
            close()
        }
        canvas.drawPath(star, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(0f, 0f, core * .17f, paint)
        canvas.restore()
        for (i in 0 until 6) {
            val a = t * (.5f + i % 2 * .2f) + i * (2f * PI.toFloat() / 6f)
            paint.color = if (i % 2 == 0) cyan else pink
            paint.alpha = 130
            canvas.drawCircle(cx + cos(a) * r * 1.4f, cy + sin(a) * r * .95f, 2.5f * density, paint)
        }
        paint.alpha = 255
        postInvalidateDelayed(33L)
    }
}
