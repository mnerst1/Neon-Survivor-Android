package com.day008.neonsurvivor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.concurrent.thread
import kotlin.math.*

class GameView(context: Context, val engine: GameEngine, private val onPauseTap: () -> Unit, private val onPulseTap: () -> Unit) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    @Volatile private var running = false
    private var loop: Thread? = null
    private var scale = 1f
    private var activePointer = -1
    private var lastWave = 1
    private var waveBanner = 0f
    private val bg = 0xFF0B1020.toInt()
    private val cyan = 0xFF00D9E8.toInt()
    private val pink = 0xFFFF3D8C.toInt()
    private val lime = 0xFF9BFF8E.toInt()
    private val pale = 0xFFEAF2FF.toInt()
    private val muted = 0xFF7986A8.toInt()

    init { holder.addCallback(this); isFocusable = true }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (running) return
        running = true
        loop = thread(name = "NeonGameLoop") {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.05f)
                last = now
                synchronized(engine) {
                    engine.update(dt)
                    if (engine.wave != lastWave) { lastWave = engine.wave; waveBanner = 2.2f }
                    waveBanner = (waveBanner - dt).coerceAtLeast(0f)
                    var canvas: Canvas? = null
                    try {
                        canvas = holder.lockCanvas()
                        if (canvas != null) drawFrame(canvas)
                    } catch (_: Exception) {
                        // Surface may disappear while the Activity is stopping.
                    } finally { if (canvas != null) holder.unlockCanvasAndPost(canvas) }
                }
                val frameMs = (System.nanoTime() - now) / 1_000_000L
                if (frameMs < 16L) Thread.sleep(16L - frameMs)
            }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(engine) {
            scale = width / 480f
            engine.resize(480f, height / scale)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        loop?.join(500)
        loop = null
    }

    private fun drawFrame(c: Canvas) {
        c.drawColor(bg)
        c.save(); c.scale(scale, scale)
        val w = engine.width; val h = engine.height
        drawGrid(c, w, h)
        drawGems(c)
        drawShots(c)
        drawEnemies(c)
        drawPlayer(c)
        drawPulse(c)
        drawSparks(c)
        drawHud(c, w, h)
        drawJoystick(c, h)
        if (waveBanner > 0f) {
            text(c, context.getString(R.string.wave_banner, engine.wave), w / 2, h * .4f, 34f, pale, true, true)
            val bossWave = when (engine.mode) {
                GameMode.SURVIVAL -> engine.wave == 5
                GameMode.BLITZ -> engine.wave == 4
                GameMode.BOSS_RUSH -> engine.wave % 2 == 1
            }
            text(c, context.getString(if (bossWave) R.string.boss_incoming else R.string.stay_moving), w / 2, h * .4f + 30f, 13f, cyan, true)
        }
        c.restore()
    }

    private fun drawGrid(c: Canvas, w: Float, h: Float) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0xFF18243C.toInt()
        val shift = (engine.elapsed * 12f) % 40f
        var x = 0f
        while (x <= w) { c.drawLine(x, 0f, x, h, paint); x += 40f }
        var y = shift
        while (y <= h) { c.drawLine(0f, y, w, y, paint); y += 40f }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0x551A2749, 0x001A2749, 0x7725193B), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint); paint.shader = null
    }

    private fun drawGems(c: Canvas) {
        for (g in engine.gems) {
            paint.color = lime; paint.style = Paint.Style.FILL
            val path = Path().apply { moveTo(g.x, g.y - 7f); lineTo(g.x + 6f, g.y); lineTo(g.x, g.y + 7f); lineTo(g.x - 6f, g.y); close() }
            c.drawPath(path, paint)
        }
    }

    private fun drawShots(c: Canvas) {
        for (s in engine.shots) {
            paint.color = if (s.friendly) if (s.rail) lime else cyan else pink
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (s.rail) 7f else 4f
            paint.strokeCap = Paint.Cap.ROUND
            c.drawLine(s.x, s.y, s.x - s.vx * .032f, s.y - s.vy * .032f, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawEnemies(c: Canvas) {
        for (e in engine.enemies) {
            val radius = when (e.kind) { EnemyKind.BOSS -> 43f; EnemyKind.TANK -> 25f; else -> 16f }
            val color = if (e.flash > 0f) Color.WHITE else when (e.kind) {
                EnemyKind.DRONE -> 0xFFFC5B82.toInt()
                EnemyKind.DASHER -> 0xFFFFA85A.toInt()
                EnemyKind.TANK -> 0xFF835EFF.toInt()
                EnemyKind.SNIPER -> 0xFFFFCF63.toInt()
                EnemyKind.BOSS -> pink
            }
            paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = if (e.kind == EnemyKind.BOSS) 4f else 3f
            when (e.kind) {
                EnemyKind.DASHER -> {
                    val a = atan2(engine.py - e.y, engine.px - e.x)
                    val path = Path().apply {
                        moveTo(e.x + cos(a) * radius, e.y + sin(a) * radius)
                        lineTo(e.x + cos(a + 2.45f) * radius, e.y + sin(a + 2.45f) * radius)
                        lineTo(e.x + cos(a - 2.45f) * radius, e.y + sin(a - 2.45f) * radius); close()
                    }
                    c.drawPath(path, paint)
                }
                EnemyKind.TANK, EnemyKind.BOSS -> drawPolygon(c, e.x, e.y, radius, if (e.kind == EnemyKind.BOSS) 8 else 6, engine.elapsed * .3f)
                EnemyKind.SNIPER -> { c.drawCircle(e.x, e.y, radius, paint); c.drawLine(e.x - 10f, e.y, e.x + 10f, e.y, paint); c.drawLine(e.x, e.y - 10f, e.x, e.y + 10f, paint) }
                else -> { c.drawCircle(e.x, e.y, radius, paint); c.drawCircle(e.x, e.y, 5f, paint) }
            }
            if (e.kind == EnemyKind.BOSS || e.kind == EnemyKind.TANK) {
                paint.style = Paint.Style.FILL; paint.color = 0xFF263149.toInt(); c.drawRoundRect(e.x - radius, e.y - radius - 13f, e.x + radius, e.y - radius - 9f, 2f, 2f, paint)
                paint.color = color; c.drawRoundRect(e.x - radius, e.y - radius - 13f, e.x - radius + 2 * radius * (e.hp / e.maxHp).coerceIn(0f, 1f), e.y - radius - 9f, 2f, 2f, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawPolygon(c: Canvas, x: Float, y: Float, r: Float, sides: Int, rotation: Float) {
        val path = Path()
        for (i in 0 until sides) {
            val a = rotation + i * 2f * PI.toFloat() / sides
            val xx = x + cos(a) * r; val yy = y + sin(a) * r
            if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
        }
        path.close(); c.drawPath(path, paint)
    }

    private fun drawPlayer(c: Canvas) {
        val p = engine
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = if (p.invincible > 0f && (p.elapsed * 18).toInt() % 2 == 0) Color.WHITE else cyan
        c.drawCircle(p.px, p.py, 17f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFFB5F8FF.toInt(); c.drawCircle(p.px, p.py, 5f, paint)
        paint.color = 0x4400D9E8; c.drawCircle(p.px, p.py, 27f + sin(p.elapsed * 5f) * 2f, paint)
        for (i in 0 until p.orbit) {
            val a = p.elapsed * 3.1f + i * (2f * PI.toFloat() / p.orbit)
            paint.color = lime; c.drawCircle(p.px + cos(a) * 64f, p.py + sin(a) * 64f, 8f, paint)
        }
    }

    private fun drawPulse(c: Canvas) {
        val p = engine
        if (p.pulseVisual > 0f) {
            val progress = 1f - p.pulseVisual / .38f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f * (1f - progress)
            paint.color = lime
            paint.alpha = ((1f - progress) * 220f).toInt().coerceIn(0, 255)
            c.drawCircle(p.px, p.py, progress * 170f, paint)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawSparks(c: Canvas) {
        paint.style = Paint.Style.FILL
        for (s in engine.sparks) { paint.color = s.color; paint.alpha = (s.life * 400f).toInt().coerceIn(0, 255); c.drawCircle(s.x, s.y, 2.3f, paint) }
        paint.alpha = 255
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        paint.color = 0xD9121A2E.toInt(); paint.style = Paint.Style.FILL
        c.drawRoundRect(14f, 14f, w - 14f, 91f, 17f, 17f, paint)
        text(c, context.getString(R.string.hud_wave, engine.wave), 29f, 42f, 15f, pale, false, true)
        text(c, context.getString(R.string.hud_score, engine.score), w / 2, 42f, 15f, pale, true, true)
        text(c, "Ⅱ", w - 43f, 46f, 26f, pale, true, true)
        paint.color = 0xFF263149.toInt(); c.drawRoundRect(28f, 58f, w - 28f, 66f, 4f, 4f, paint)
        paint.color = pink; c.drawRoundRect(28f, 58f, 28f + (w - 56f) * (engine.hp / engine.maxHp).coerceIn(0f, 1f), 66f, 4f, 4f, paint)
        paint.color = 0xFF263149.toInt(); c.drawRoundRect(28f, 75f, w - 28f, 81f, 3f, 3f, paint)
        paint.color = cyan; c.drawRoundRect(28f, 75f, 28f + (w - 56f) * (engine.xp.toFloat() / engine.xpTarget).coerceIn(0f, 1f), 81f, 3f, 3f, paint)
        text(c, context.getString(R.string.hud_level, engine.level), 30f, 110f, 13f, muted)
        val seconds = if (engine.mode == GameMode.BLITZ) (90 - engine.elapsed.toInt()).coerceAtLeast(0) else engine.elapsed.toInt()
        text(c, "%02d:%02d".format(seconds / 60, seconds % 60), w - 30f, 110f, 13f, if (engine.mode == GameMode.BLITZ) lime else muted, false, false, true)
        engine.bossAlive()?.let { boss ->
            text(c, context.getString(R.string.boss_name), w / 2, 144f, 14f, pink, true, true)
            paint.color = 0xFF263149.toInt(); c.drawRoundRect(62f, 153f, w - 62f, 161f, 4f, 4f, paint)
            paint.color = pink; c.drawRoundRect(62f, 153f, 62f + (w - 124f) * (boss.hp / boss.maxHp).coerceIn(0f, 1f), 161f, 4f, 4f, paint)
        }
        text(c, context.getString(R.string.move_hint), w / 2, h - 28f, 13f, muted, true)
        drawPulseButton(c, w, h)
    }

    private fun drawPulseButton(c: Canvas, w: Float, h: Float) {
        val x = w - 78f; val y = h - 105f
        paint.style = Paint.Style.FILL
        paint.color = 0xCC172943.toInt()
        c.drawCircle(x, y, 48f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = if (engine.pulseCooldown <= 0f) lime else 0xFF566177.toInt()
        c.drawCircle(x, y, 45f, paint)
        paint.style = Paint.Style.FILL
        text(c, if (engine.pulseCooldown <= 0f) context.getString(R.string.pulse) else engine.pulseCooldown.toInt().plus(1).toString(), x, y + 5f, 16f, if (engine.pulseCooldown <= 0f) lime else muted, true, true)
    }

    private fun drawJoystick(c: Canvas, h: Float) {
        val x = if (engine.joystickActive) engine.joystickBaseX else 90f
        val y = if (engine.joystickActive) engine.joystickBaseY else h - 105f
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0x664FD7E2
        c.drawCircle(x, y, 53f, paint)
        paint.style = Paint.Style.FILL; paint.color = 0x7739AAB8
        c.drawCircle(if (engine.joystickActive) engine.joystickKnobX else x, if (engine.joystickActive) engine.joystickKnobY else y, 22f, paint)
    }

    private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float, color: Int, center: Boolean = false, bold: Boolean = false, right: Boolean = false) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.typeface = if (bold) android.graphics.Typeface.create("sans-serif-medium", 0) else android.graphics.Typeface.create("sans-serif", 0)
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        c.drawText(value, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(engine) {
            val x = event.x / scale; val y = event.y / scale
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (y < 95f && x > engine.width - 85f) { onPauseTap(); return true }
                    if (hypot(x - (engine.width - 78f), y - (engine.height - 105f)) < 55f) {
                        if (engine.pulse()) onPulseTap()
                        return true
                    }
                    if (activePointer == -1) { activePointer = event.getPointerId(event.actionIndex); engine.joystickActive = true; engine.joystickBaseX = x; engine.joystickBaseY = y; updateJoystick(x, y) }
                }
                MotionEvent.ACTION_MOVE -> {
                    val idx = event.findPointerIndex(activePointer)
                    if (idx >= 0) updateJoystick(event.getX(idx) / scale, event.getY(idx) / scale)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.getPointerId(event.actionIndex) == activePointer) {
                        activePointer = -1; engine.joystickActive = false; engine.joystickX = 0f; engine.joystickY = 0f
                    }
                }
            }
        }
        return true
    }

    private fun updateJoystick(x: Float, y: Float) {
        val dx = x - engine.joystickBaseX; val dy = y - engine.joystickBaseY
        val length = hypot(dx, dy)
        val factor = if (length > 60f) 60f / length else 1f
        engine.joystickKnobX = engine.joystickBaseX + dx * factor
        engine.joystickKnobY = engine.joystickBaseY + dy * factor
        engine.joystickX = dx * factor / 60f
        engine.joystickY = dy * factor / 60f
    }
}
