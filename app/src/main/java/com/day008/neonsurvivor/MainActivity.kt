package com.day008.neonsurvivor

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private val ink = 0xFF0B1020.toInt()
    private val card = 0xFF171F34.toInt()
    private val cyan = 0xFF00D9E8.toInt()
    private val pink = 0xFFFF3D8C.toInt()
    private val pale = 0xFFEAF2FF.toInt()
    private val muted = 0xFF9BA8C3.toInt()
    private val prefs by lazy { getSharedPreferences("neon_save_v1", MODE_PRIVATE) }
    private var engine: GameEngine? = null
    private var gameView: GameView? = null
    private var dialog: AlertDialog? = null
    private var tone: ToneGenerator? = null
    private val selectedMode: GameMode
        get() = runCatching { GameMode.valueOf(prefs.getString("mode", GameMode.SURVIVAL.name) ?: GameMode.SURVIVAL.name) }.getOrDefault(GameMode.SURVIVAL)

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("neon_save_v1", MODE_PRIVATE).getString("language", "en") ?: "en"
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = ink
        window.decorView.systemUiVisibility = 0
        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 40) } catch (_: Exception) { }
        showMenu()
    }

    private fun background(): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(ink, 0xFF151A32.toInt(), ink))
    private fun rounded(color: Int, radius: Float = 22f): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius * resources.displayMetrics.density }

    private fun label(text: String, size: Float, color: Int = pale, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(text: String, color: Int = cyan, darkText: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        this.text = text; textSize = 15f; isAllCaps = false
        setTextColor(if (darkText) ink else pale)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        background = rounded(color, 16f)
        elevation = 2f * resources.displayMetrics.density
        setOnClickListener { action() }
    }

    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(26), dp(24), dp(26), dp(28)) }
    private fun gap(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun add(parent: LinearLayout, view: View, height: Int = -2) { parent.addView(view, LinearLayout.LayoutParams(-1, if (height < 0) height else dp(height))) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setSafeContent(content: View, motionBackground: Boolean = false) {
        val frame = FrameLayout(this).apply {
            background = background()
            setOnApplyWindowInsetsListener { view, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    top = safe.top; bottom = safe.bottom
                } else {
                    @Suppress("DEPRECATION")
                    top = insets.systemWindowInsetTop
                    @Suppress("DEPRECATION")
                    bottom = insets.systemWindowInsetBottom
                }
                view.setPadding(0, top, 0, bottom)
                insets
            }
        }
        if (motionBackground) frame.addView(MenuBackdrop(this), FrameLayout.LayoutParams(-1, -1))
        frame.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        frame.requestApplyInsets()
    }

    private fun screen(content: LinearLayout, animated: Boolean = false) {
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content); setBackgroundColor(Color.TRANSPARENT) }
        setSafeContent(scroll, animated)
    }

    private fun enter(vararg views: View) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(17).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(460L).setStartDelay(index * 70L).start()
        }
    }

    private fun showMenu() {
        dialog?.dismiss(); dialog = null
        gameView = null; engine = null
        val root = column()
        gap(root, 14)
        val eyebrow = label(getString(R.string.day008), 14f, cyan, true)
        add(root, eyebrow)
        gap(root, 8)
        val title = label(getString(R.string.app_name).uppercase(Locale.ROOT), 42f, pale, true)
        add(root, title)
        gap(root, 9)
        val subtitle = label(getString(R.string.tagline), 16f, muted)
        add(root, subtitle)
        gap(root, 27)
        val emblem = HeroView(this)
        add(root, emblem, 190)
        gap(root, 24)
        val mode = selectedMode
        val modeCard = column().apply {
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rounded(card, 16f)
            add(this, label(getString(modeTitle(mode)), 17f, cyan, true))
            add(this, label(getString(modeDesc(mode)), 13f, muted))
            setOnClickListener { showModePicker() }
        }
        add(root, modeCard)
        gap(root, 18)
        val score = label(getString(R.string.high_score, prefs.getInt(scoreKey(mode), 0)), 20f, pale, true)
        add(root, score)
        gap(root, 5)
        val best = label(getString(R.string.best_wave, prefs.getInt(waveKey(mode), 0)), 14f, muted)
        add(root, best)
        gap(root, 19)
        val play = button(getString(R.string.play)) { startGame(mode) }
        add(root, play, 55)
        gap(root, 12)
        val stats = button(getString(R.string.statistics), card, false) { showStatistics() }
        add(root, stats, 52)
        gap(root, 12)
        val achievements = button(getString(R.string.achievements), card, false) { showAchievements() }
        add(root, achievements, 52)
        gap(root, 12)
        val settings = button(getString(R.string.settings), card, false) { showSettings() }
        add(root, settings, 52)
        gap(root, 26)
        add(root, label(getString(R.string.menu_hint), 13f, muted))
        screen(root, true)
        enter(eyebrow, title, subtitle, emblem, modeCard, score, best, play, stats, achievements, settings)
    }

    private fun showModePicker() {
        val modes = GameMode.entries
        val names = modes.map { getString(modeTitle(it)) + " — " + getString(modeDesc(it)) }.toTypedArray()
        dialog = AlertDialog.Builder(this).setTitle(getString(R.string.choose_mode))
            .setSingleChoiceItems(names, modes.indexOf(selectedMode)) { chooser, index ->
                prefs.edit().putString("mode", modes[index].name).apply()
                chooser.dismiss(); dialog = null; showMenu()
            }.setNegativeButton(getString(R.string.back)) { _, _ -> dialog = null }
            .create().also { it.show(); styleDialog(it) }
    }

    private fun startGame(mode: GameMode = selectedMode) {
        dialog?.dismiss(); dialog = null
        val newEngine = GameEngine(mode,
            onLevel = { choices -> runOnUiThread { showUpgrade(choices) } },
            onOver = { score, wave, unlocks -> runOnUiThread { finishRun(score, wave, unlocks) } },
            onAchievement = { id -> runOnUiThread { saveAchievement(id); feedback(true) } }
        )
        engine = newEngine
        val view = GameView(this, newEngine, { runOnUiThread { showPause() } }, { runOnUiThread { feedback(false) } })
        gameView = view
        setSafeContent(view)
    }

    private fun showUpgrade(choices: List<Upgrade>) {
        val current = engine ?: return
        if (current.ended) return
        feedback(false)
        val box = column().apply { setPadding(dp(8), dp(5), dp(8), dp(5)) }
        choices.forEach { upgrade ->
            add(box, button(getString(upgradeTitle(upgrade)) + "\n" + getString(upgradeDesc(upgrade)), card, false) {
                synchronized(current) { current.choose(upgrade) }
                feedback(false)
                dialog?.dismiss(); dialog = null
            }, 82)
            gap(box, 10)
        }
        val builder = AlertDialog.Builder(this).setTitle(getString(R.string.choose_upgrade)).setView(box).setCancelable(false)
        if (current.rerollsLeft > 0) builder.setNeutralButton(getString(R.string.reroll, current.rerollsLeft)) { _, _ ->
            val next = synchronized(current) { current.reroll() }
            dialog = null
            if (next != null) showUpgrade(next)
        }
        dialog = builder.create().also { it.show(); styleDialog(it) }
    }

    private fun showPause() {
        val current = engine ?: return
        if (current.ended || dialog != null) return
        synchronized(current) { current.paused = true; current.joystickX = 0f; current.joystickY = 0f }
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.paused))
            .setMessage(getString(R.string.pause_message))
            .setPositiveButton(getString(R.string.resume)) { _, _ -> synchronized(current) { current.paused = false }; dialog = null }
            .setNeutralButton(getString(R.string.restart)) { _, _ -> recordRun(current); dialog = null; startGame(current.mode) }
            .setNegativeButton(getString(R.string.main_menu)) { _, _ -> recordRun(current); dialog = null; showMenu() }
            .setCancelable(false).create().also { it.show(); styleDialog(it) }
    }

    private fun finishRun(score: Int, wave: Int, unlocks: Set<String>) {
        if (engine == null) return
        val current = engine ?: return
        val best = maxOf(score, prefs.getInt(scoreKey(current.mode), 0))
        recordRun(current)
        unlocks.forEach { saveAchievement(it) }
        feedback(true)
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(if (current.timeComplete) R.string.run_complete else R.string.game_over))
            .setMessage(getString(R.string.final_score, score, wave, best))
            .setPositiveButton(getString(R.string.restart)) { _, _ -> dialog = null; startGame(current.mode) }
            .setNegativeButton(getString(R.string.main_menu)) { _, _ -> dialog = null; showMenu() }
            .setCancelable(false).create().also { it.show(); styleDialog(it) }
    }

    private fun showAchievements() {
        val root = column()
        add(root, label(getString(R.string.achievements), 31f, pale, true))
        gap(root, 5)
        add(root, label(getString(R.string.achievement_intro), 14f, muted))
        gap(root, 25)
        listOf("first_blood", "centurion", "boss", "survivor", "level_ten", "arsenal").forEach { id ->
            val unlocked = prefs.getBoolean("achievement_$id", false)
            val cardView = column().apply {
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = rounded(card, 15f)
                add(this, label((if (unlocked) "◆  " else "◇  ") + getString(achievementTitle(id)), 18f, if (unlocked) cyan else pale, true))
                add(this, label(getString(achievementDesc(id)), 13f, muted))
            }
            add(root, cardView)
            gap(root, 10)
        }
        gap(root, 18)
        add(root, button(getString(R.string.back)) { showMenu() }, 52)
        screen(root)
    }

    private fun showStatistics() {
        val root = column()
        add(root, label(getString(R.string.statistics), 31f, pale, true))
        gap(root, 5)
        add(root, label(getString(R.string.stats_intro), 14f, muted))
        gap(root, 23)
        val rows = listOf(
            R.string.stat_runs to prefs.getInt("stat_runs", 0),
            R.string.stat_kills to prefs.getInt("stat_kills", 0),
            R.string.stat_bosses to prefs.getInt("stat_bosses", 0),
            R.string.stat_minutes to prefs.getInt("stat_seconds", 0) / 60
        )
        rows.forEach { (title, value) ->
            val row = column().apply {
                setPadding(dp(18), dp(12), dp(18), dp(12))
                background = rounded(card, 15f)
                add(this, label(getString(title), 14f, muted))
                add(this, label(value.toString(), 27f, cyan, true))
            }
            add(root, row); gap(root, 10)
        }
        gap(root, 10)
        GameMode.entries.forEach { mode ->
            add(root, label(getString(modeTitle(mode)), 16f, pale, true))
            add(root, label(getString(R.string.mode_record, prefs.getInt(scoreKey(mode), 0), prefs.getInt(waveKey(mode), 0)), 14f, muted))
            gap(root, 12)
        }
        gap(root, 10)
        add(root, button(getString(R.string.back)) { showMenu() }, 52)
        screen(root)
    }

    private fun showSettings() {
        val root = column()
        add(root, label(getString(R.string.settings), 31f, pale, true))
        gap(root, 7)
        add(root, label(getString(R.string.settings_intro), 14f, muted))
        gap(root, 26)
        fun toggle(title: String, key: String) {
            val sw = Switch(this).apply {
                text = title; textSize = 17f; setTextColor(pale)
                isChecked = prefs.getBoolean(key, true)
                setPadding(dp(15), dp(9), dp(15), dp(9)); background = rounded(card, 14f)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
            }
            add(root, sw, 62); gap(root, 12)
        }
        toggle(getString(R.string.sound), "sound")
        toggle(getString(R.string.vibration), "vibration")
        add(root, label(getString(R.string.language), 15f, muted, true))
        gap(root, 9)
        val names = arrayOf("English", "Қазақша", "Русский")
        val codes = arrayOf("en", "kk", "ru")
        val selected = codes.indexOf(prefs.getString("language", "en")).coerceAtLeast(0)
        add(root, button(names[selected], card, false) {
            AlertDialog.Builder(this).setTitle(getString(R.string.language)).setSingleChoiceItems(names, selected) { chooser, index ->
                prefs.edit().putString("language", codes[index]).apply()
                chooser.dismiss(); recreate()
            }.show()
        }, 54)
        gap(root, 35)
        add(root, button(getString(R.string.back)) { showMenu() }, 52)
        screen(root)
    }

    private fun styleDialog(d: AlertDialog) {
        d.window?.setBackgroundDrawable(rounded(card, 22f))
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cyan)
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(pink)
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(pale)
    }

    private fun feedback(long: Boolean) {
        if (prefs.getBoolean("sound", true)) try { tone?.startTone(if (long) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, if (long) 180 else 80) } catch (_: Exception) { }
        if (prefs.getBoolean("vibration", true)) try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
            vibrator.vibrate(VibrationEffect.createOneShot(if (long) 110L else 35L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
    }

    private fun saveAchievement(id: String) { prefs.edit().putBoolean("achievement_$id", true).apply() }
    private fun scoreKey(mode: GameMode) = if (mode == GameMode.SURVIVAL) "high_score" else "high_score_${mode.name}"
    private fun waveKey(mode: GameMode) = if (mode == GameMode.SURVIVAL) "best_wave" else "best_wave_${mode.name}"
    private fun modeTitle(mode: GameMode): Int = when (mode) { GameMode.SURVIVAL -> R.string.mode_survival; GameMode.BLITZ -> R.string.mode_blitz; GameMode.BOSS_RUSH -> R.string.mode_boss_rush }
    private fun modeDesc(mode: GameMode): Int = when (mode) { GameMode.SURVIVAL -> R.string.mode_survival_desc; GameMode.BLITZ -> R.string.mode_blitz_desc; GameMode.BOSS_RUSH -> R.string.mode_boss_rush_desc }
    private fun saveRunSnapshot(current: GameEngine) {
        synchronized(current) {
            prefs.edit()
                .putInt(scoreKey(current.mode), maxOf(current.score, prefs.getInt(scoreKey(current.mode), 0)))
                .putInt(waveKey(current.mode), maxOf(current.wave, prefs.getInt(waveKey(current.mode), 0)))
                .apply()
        }
    }
    private fun recordRun(current: GameEngine) {
        synchronized(current) {
            if (current.statsRecorded) return
            current.statsRecorded = true
            saveRunSnapshot(current)
            prefs.edit()
                .putInt("stat_runs", prefs.getInt("stat_runs", 0) + 1)
                .putInt("stat_kills", prefs.getInt("stat_kills", 0) + current.kills)
                .putInt("stat_bosses", prefs.getInt("stat_bosses", 0) + current.bossKills)
                .putInt("stat_seconds", prefs.getInt("stat_seconds", 0) + current.elapsed.toInt())
                .apply()
        }
    }
    private fun upgradeTitle(u: Upgrade): Int = when (u) { Upgrade.DAMAGE -> R.string.up_damage; Upgrade.FIRE_RATE -> R.string.up_rate; Upgrade.SPEED -> R.string.up_speed; Upgrade.MAX_HP -> R.string.up_hp; Upgrade.SPREAD -> R.string.up_spread; Upgrade.ORBIT -> R.string.up_orbit; Upgrade.RAIL -> R.string.up_rail; Upgrade.HEAL -> R.string.up_heal }
    private fun upgradeDesc(u: Upgrade): Int = when (u) { Upgrade.DAMAGE -> R.string.desc_damage; Upgrade.FIRE_RATE -> R.string.desc_rate; Upgrade.SPEED -> R.string.desc_speed; Upgrade.MAX_HP -> R.string.desc_hp; Upgrade.SPREAD -> R.string.desc_spread; Upgrade.ORBIT -> R.string.desc_orbit; Upgrade.RAIL -> R.string.desc_rail; Upgrade.HEAL -> R.string.desc_heal }
    private fun achievementTitle(id: String): Int = when (id) { "first_blood" -> R.string.ach_first; "centurion" -> R.string.ach_centurion; "boss" -> R.string.ach_boss; "survivor" -> R.string.ach_survivor; "level_ten" -> R.string.ach_level; else -> R.string.ach_arsenal }
    private fun achievementDesc(id: String): Int = when (id) { "first_blood" -> R.string.ach_first_desc; "centurion" -> R.string.ach_centurion_desc; "boss" -> R.string.ach_boss_desc; "survivor" -> R.string.ach_survivor_desc; "level_ten" -> R.string.ach_level_desc; else -> R.string.ach_arsenal_desc }

    override fun onPause() { super.onPause(); engine?.let { synchronized(it) { it.paused = true }; saveRunSnapshot(it) } }
    override fun onResume() { super.onResume(); if (engine != null && dialog == null) window.decorView.post { showPause() } }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { if (engine != null) showPause() else showMenu() }
    override fun onDestroy() { tone?.release(); super.onDestroy() }
}
