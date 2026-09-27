package com.day008.neonsurvivor

import kotlin.math.*
import kotlin.random.Random

enum class EnemyKind { DRONE, DASHER, TANK, SNIPER, BOSS }
enum class Upgrade { DAMAGE, FIRE_RATE, SPEED, MAX_HP, SPREAD, ORBIT, RAIL, HEAL }
enum class GameMode(val waveSeconds: Float, val scoreMultiplier: Int) {
    SURVIVAL(28f, 1), BLITZ(20f, 2), BOSS_RUSH(32f, 2)
}

data class Enemy(var x: Float, var y: Float, val kind: EnemyKind, var hp: Float, val maxHp: Float, var attack: Float = 0f, var flash: Float = 0f)
data class Shot(var x: Float, var y: Float, var vx: Float, var vy: Float, val damage: Float, val friendly: Boolean, var life: Float, var pierce: Int = 0, val rail: Boolean = false)
data class Gem(var x: Float, var y: Float, val value: Int)
data class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)

class GameEngine(val mode: GameMode, private val onLevel: (List<Upgrade>) -> Unit, private val onOver: (Int, Int, Set<String>) -> Unit, private val onAchievement: (String) -> Unit) {
    var width = 480f
    var height = 800f
    var px = width / 2
    var py = height / 2
    var hp = 100f
    var maxHp = 100f
    var level = 1
    var xp = 0
    var xpTarget = 12
    var score = 0
    var kills = 0
    var wave = 1
    var elapsed = 0f
    var paused = false
    var ended = false
    var invincible = 0f
    var damage = 18f
    var fireInterval = 0.48f
    var speed = 185f
    var spread = 0
    var orbit = 0
    var rail = 0
    var rerollsLeft = 1
    var pulseCooldown = 0f
    var pulseVisual = 0f
    var timeComplete = false
    var bossKills = 0
    var statsRecorded = false
    var joystickX = 0f
    var joystickY = 0f
    var joystickActive = false
    var joystickBaseX = 0f
    var joystickBaseY = 0f
    var joystickKnobX = 0f
    var joystickKnobY = 0f
    val enemies = mutableListOf<Enemy>()
    val shots = mutableListOf<Shot>()
    val gems = mutableListOf<Gem>()
    val sparks = mutableListOf<Spark>()
    val achievements = linkedSetOf<String>()
    private var fireClock = 0f
    private var spawnClock = 0f
    private var railClock = 0f
    private var orbitClock = 0f
    private var lastBossWave = 0
    private var bossKilled = false
    private var levelPending = false
    private val random = Random.Default

    fun resize(w: Float, h: Float) {
        width = w; height = h
        px = px.coerceIn(22f, width - 22f)
        py = py.coerceIn(22f, height - 22f)
    }

    fun choose(upgrade: Upgrade) {
        when (upgrade) {
            Upgrade.DAMAGE -> damage *= 1.24f
            Upgrade.FIRE_RATE -> fireInterval = (fireInterval * 0.82f).coerceAtLeast(0.12f)
            Upgrade.SPEED -> speed *= 1.17f
            Upgrade.MAX_HP -> { maxHp += 25f; hp = (hp + 25f).coerceAtMost(maxHp) }
            Upgrade.SPREAD -> spread = (spread + 1).coerceAtMost(3)
            Upgrade.ORBIT -> orbit = (orbit + 1).coerceAtMost(3)
            Upgrade.RAIL -> rail = (rail + 1).coerceAtMost(3)
            Upgrade.HEAL -> hp = (hp + 45f).coerceAtMost(maxHp)
        }
        if (spread > 0 && orbit > 0 && rail > 0) award("arsenal")
        levelPending = false
        paused = false
    }

    fun reroll(): List<Upgrade>? {
        if (!levelPending || rerollsLeft <= 0) return null
        rerollsLeft--
        return upgradePool().shuffled(random).take(3)
    }

    fun pulse(): Boolean {
        if (paused || ended || levelPending || pulseCooldown > 0f) return false
        pulseCooldown = 12f
        pulseVisual = .38f
        val radius = 170f
        for (enemy in enemies) {
            val dx = enemy.x - px; val dy = enemy.y - py
            val distance = hypot(dx, dy)
            if (distance < radius) {
                enemy.hp -= if (enemy.kind == EnemyKind.BOSS) 75f else 95f
                enemy.flash = .15f
                val force = (radius - distance) * .32f
                enemy.x += dx / distance.coerceAtLeast(1f) * force
                enemy.y += dy / distance.coerceAtLeast(1f) * force
            }
        }
        shots.removeAll { !it.friendly && hypot(it.x - px, it.y - py) < radius }
        burst(px, py, 0xFF9BFF8E.toInt(), 32)
        return true
    }

    fun update(rawDt: Float) {
        if (paused || ended || levelPending) return
        val dt = rawDt.coerceIn(0f, 0.05f)
        elapsed += dt
        if (mode == GameMode.BLITZ && elapsed >= 90f) {
            timeComplete = true; ended = true; paused = true
            onOver(score, wave, achievements)
            return
        }
        pulseCooldown = (pulseCooldown - dt).coerceAtLeast(0f)
        pulseVisual = (pulseVisual - dt).coerceAtLeast(0f)
        val newWave = (elapsed / mode.waveSeconds).toInt() + 1
        if (newWave != wave) { wave = newWave; spawnClock = 0f; burst(px, py, 0xFF00D9E8.toInt(), 18) }
        val bossWave = when (mode) {
            GameMode.SURVIVAL -> wave == 5
            GameMode.BLITZ -> wave == 4
            GameMode.BOSS_RUSH -> wave % 2 == 1
        }
        if (bossWave && wave != lastBossWave) { spawnBoss(); lastBossWave = wave }
        if (elapsed >= 180f) award("survivor")
        invincible = (invincible - dt).coerceAtLeast(0f)
        px = (px + joystickX * speed * dt).coerceIn(18f, width - 18f)
        py = (py + joystickY * speed * dt).coerceIn(18f, height - 18f)
        spawnClock -= dt
        if (spawnClock <= 0f && enemies.size < 85) {
            val amount = 1 + wave / 3
            repeat(amount.coerceAtMost(5)) { spawnEnemy() }
            spawnClock = (1.05f - wave * 0.06f).coerceAtLeast(0.24f)
        }
        fireClock -= dt
        if (fireClock <= 0f) { fireAtNearest(); fireClock = fireInterval }
        railClock -= dt
        if (rail > 0 && railClock <= 0f) { fireRail(); railClock = 2.8f / rail }
        orbitClock += dt
        moveEnemies(dt)
        moveShots(dt)
        collectGems(dt)
        sparks.removeAll { it.life <= 0f }
        sparks.forEach { it.x += it.vx * dt; it.y += it.vy * dt; it.life -= dt }
        if (hp <= 0f) { ended = true; paused = true; onOver(score, wave, achievements) }
    }

    private fun spawnEnemy() {
        val side = random.nextInt(4)
        val x = when (side) { 0 -> -30f; 1 -> width + 30f; else -> random.nextFloat() * width }
        val y = when (side) { 2 -> -30f; 3 -> height + 30f; else -> random.nextFloat() * height }
        val roll = random.nextFloat()
        val kind = when {
            wave >= 3 && roll > .86f -> EnemyKind.SNIPER
            wave >= 2 && roll > .67f -> EnemyKind.TANK
            roll > .39f -> EnemyKind.DASHER
            else -> EnemyKind.DRONE
        }
        val base = when (kind) { EnemyKind.DRONE -> 26f; EnemyKind.DASHER -> 19f; EnemyKind.TANK -> 76f; EnemyKind.SNIPER -> 34f; EnemyKind.BOSS -> 900f }
        val life = base * (1f + (wave - 1) * .13f)
        enemies += Enemy(x, y, kind, life, life, random.nextFloat())
    }

    private fun spawnBoss() {
        val life = if (mode == GameMode.BOSS_RUSH) 490f + 140f * (wave - 1) else 1100f + 180f * (wave - 5).coerceAtLeast(0)
        enemies += Enemy(width / 2, -70f, EnemyKind.BOSS, life, life)
        burst(width / 2, 80f, 0xFFFF3D8C.toInt(), 35)
    }

    private fun moveEnemies(dt: Float) {
        for (e in enemies) {
            e.flash = (e.flash - dt).coerceAtLeast(0f)
            e.attack -= dt
            val dx = px - e.x; val dy = py - e.y
            val dist = hypot(dx, dy).coerceAtLeast(1f)
            val velocity = when (e.kind) {
                EnemyKind.DRONE -> 73f
                EnemyKind.DASHER -> 130f
                EnemyKind.TANK -> 48f
                EnemyKind.SNIPER -> if (dist > 190f) 60f else -32f
                EnemyKind.BOSS -> 45f
            } * (1f + (wave - 1) * .035f)
            e.x += dx / dist * velocity * dt
            e.y += dy / dist * velocity * dt
            if ((e.kind == EnemyKind.SNIPER || e.kind == EnemyKind.BOSS) && e.attack <= 0f && dist < 550f) {
                val count = if (e.kind == EnemyKind.BOSS) 8 else 1
                repeat(count) { n ->
                    val a = if (count == 1) atan2(dy, dx) else n * (PI.toFloat() * 2f / count) + elapsed * .4f
                    shots += Shot(e.x, e.y, cos(a) * 150f, sin(a) * 150f, if (count == 1) 11f else 15f, false, 3.4f)
                }
                e.attack = if (e.kind == EnemyKind.BOSS) 2.3f else 2f
            }
            val radius = when (e.kind) { EnemyKind.BOSS -> 46f; EnemyKind.TANK -> 26f; else -> 17f }
            if (dist < radius + 15f && invincible <= 0f) {
                hp -= if (e.kind == EnemyKind.BOSS) 24f else if (e.kind == EnemyKind.TANK) 15f else 9f
                invincible = .65f
                burst(px, py, 0xFFFF3D8C.toInt(), 10)
            }
        }
        if (orbit > 0) {
            for (i in 0 until orbit) {
                val a = orbitClock * 3.1f + i * (2f * PI.toFloat() / orbit)
                val ox = px + cos(a) * 64f; val oy = py + sin(a) * 64f
                enemies.forEach { e -> if (hypot(ox - e.x, oy - e.y) < 24f + if (e.kind == EnemyKind.BOSS) 28f else 0f) e.hp -= 27f * dt }
            }
        }
        val dead = enemies.filter { it.hp <= 0f }
        dead.forEach { kill(it) }
        enemies.removeAll(dead.toSet())
    }

    private fun fireAtNearest() {
        val target = enemies.minByOrNull { (it.x - px).pow(2) + (it.y - py).pow(2) } ?: return
        val angle = atan2(target.y - py, target.x - px)
        val angles = if (spread == 0) listOf(angle) else (-spread..spread).map { angle + it * .16f }
        angles.forEach { a -> shots += Shot(px, py, cos(a) * 440f, sin(a) * 440f, damage, true, 1.65f) }
        burst(px + cos(angle) * 18f, py + sin(angle) * 18f, 0xFF00D9E8.toInt(), 2)
    }

    private fun fireRail() {
        val target = enemies.minByOrNull { (it.x - px).pow(2) + (it.y - py).pow(2) } ?: return
        val a = atan2(target.y - py, target.x - px)
        shots += Shot(px, py, cos(a) * 650f, sin(a) * 650f, damage * (2.3f + rail * .6f), true, 1.4f, 3 + rail, true)
    }

    private fun moveShots(dt: Float) {
        val remove = mutableSetOf<Shot>()
        for (s in shots) {
            s.x += s.vx * dt; s.y += s.vy * dt; s.life -= dt
            if (s.life <= 0f || s.x < -50f || s.x > width + 50f || s.y < -50f || s.y > height + 50f) { remove += s; continue }
            if (s.friendly) {
                for (e in enemies) {
                    val radius = when (e.kind) { EnemyKind.BOSS -> 48f; EnemyKind.TANK -> 28f; else -> 19f }
                    if (hypot(s.x - e.x, s.y - e.y) < radius + if (s.rail) 8f else 5f) {
                        e.hp -= s.damage; e.flash = .08f
                        burst(s.x, s.y, 0xFFFFCA59.toInt(), 3)
                        if (s.pierce <= 0) { remove += s; break } else s.pierce--
                    }
                }
            } else if (hypot(s.x - px, s.y - py) < 18f) {
                if (invincible <= 0f) { hp -= s.damage; invincible = .65f; burst(px, py, 0xFFFF3D8C.toInt(), 8) }
                remove += s
            }
        }
        shots.removeAll(remove)
        val dead = enemies.filter { it.hp <= 0f }
        dead.forEach { kill(it) }; enemies.removeAll(dead.toSet())
    }

    private fun kill(e: Enemy) {
        kills++
        score += mode.scoreMultiplier * when (e.kind) { EnemyKind.BOSS -> 1000; EnemyKind.TANK -> 35; EnemyKind.SNIPER -> 30; EnemyKind.DASHER -> 20; else -> 15 }
        val value = when (e.kind) { EnemyKind.BOSS -> 35; EnemyKind.TANK -> 5; EnemyKind.SNIPER -> 4; else -> 2 }
        gems += Gem(e.x, e.y, value)
        burst(e.x, e.y, if (e.kind == EnemyKind.BOSS) 0xFFFF3D8C.toInt() else 0xFF00D9E8.toInt(), if (e.kind == EnemyKind.BOSS) 45 else 8)
        if (e.kind == EnemyKind.BOSS) { bossKilled = true; bossKills++; award("boss") }
        if (kills >= 1) award("first_blood")
        if (kills >= 100) award("centurion")
    }

    private fun collectGems(dt: Float) {
        val collected = mutableListOf<Gem>()
        for (gem in gems) {
            val dx = px - gem.x; val dy = py - gem.y; val dist = hypot(dx, dy).coerceAtLeast(1f)
            if (dist < 105f) { val v = (240f + (105f - dist) * 3f) * dt; gem.x += dx / dist * v; gem.y += dy / dist * v }
            if (dist < 20f) { xp += gem.value; collected += gem; burst(gem.x, gem.y, 0xFF9BFF8E.toInt(), 3) }
        }
        gems.removeAll(collected.toSet())
        if (xp >= xpTarget && !levelPending) {
            xp -= xpTarget; level++; xpTarget = (xpTarget * 1.28f + 6).toInt()
            levelPending = true; paused = true
            if (level >= 10) award("level_ten")
            onLevel(upgradePool().shuffled(random).take(3))
        }
    }

    private fun upgradePool(): List<Upgrade> = Upgrade.entries.filter {
        when (it) { Upgrade.SPREAD -> spread < 3; Upgrade.ORBIT -> orbit < 3; Upgrade.RAIL -> rail < 3; else -> true }
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int) {
        repeat(count) {
            val a = random.nextFloat() * PI.toFloat() * 2f
            val v = 25f + random.nextFloat() * 115f
            sparks += Spark(x, y, cos(a) * v, sin(a) * v, .2f + random.nextFloat() * .45f, color)
        }
        if (sparks.size > 280) sparks.subList(0, sparks.size - 280).clear()
    }

    private fun award(id: String) { if (achievements.add(id)) onAchievement(id) }
    fun bossAlive(): Enemy? = enemies.firstOrNull { it.kind == EnemyKind.BOSS }
    fun bossDefeated(): Boolean = bossKilled
}
