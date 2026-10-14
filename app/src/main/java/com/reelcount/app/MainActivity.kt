package com.reelcount.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private val ink = Color.rgb(27, 35, 31)
    private val muted = Color.rgb(119, 128, 121)
    private val paper = Color.rgb(245, 246, 242)
    private val green = Color.rgb(185, 243, 107)
    private val prefs by lazy { getSharedPreferences("reel_count", MODE_PRIVATE) }
    private var selectedPlatform = "Instagram"
    private var onboardingStep = 0
    private var onboardingTime: String? = null
    private var onboardingGoal: Int? = null
    private val onboardingPreferences = mutableMapOf<String, Boolean>()
    private lateinit var page: LinearLayout
    private var usageTotalView: TextView? = null
    private var usageAccessHint: TextView? = null
    private val usagePlatformViews = mutableMapOf<String, TextView>()
    private val usageRefreshHandler = Handler(Looper.getMainLooper())
    private val usageRefreshTask = object : Runnable {
        override fun run() {
            if (prefs.getBoolean("onboarding_complete", false)) refreshUsageViews()
            usageRefreshHandler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 8, 7)
        window.navigationBarColor = paper
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        onboardingTime = prefs.getString("onboard_time", null)
        onboardingGoal = prefs.getInt("onboard_goal", -1).takeIf { it > 0 }
        onboardingPreferences["show_count"] = prefs.getBoolean("pref_show_count", true)
        onboardingPreferences["count_all_day"] = prefs.getBoolean("pref_count_all_day", true)
        onboardingPreferences["count_every_logged_reel"] = prefs.getBoolean("pref_count_every_logged_reel", true)
        if (prefs.getBoolean("onboarding_complete", false)) render() else renderOnboarding()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("onboarding_complete", false)) render()
        usageRefreshHandler.removeCallbacks(usageRefreshTask)
        usageRefreshHandler.postDelayed(usageRefreshTask, 30_000L)
    }

    override fun onPause() {
        usageRefreshHandler.removeCallbacks(usageRefreshTask)
        super.onPause()
    }

    private fun render() {
        window.statusBarColor = paper
        window.navigationBarColor = paper
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this).apply {
            setBackgroundColor(paper)
            clipToPadding = false
            isFillViewport = true
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(30))
        }
        scroll.addView(page)
        setContentView(scroll)

        val today = LocalDate.now()
        val todayKey = today.toString()
        val total = totalFor(todayKey)
        val goal = prefs.getInt("goal", 60).coerceAtLeast(1)
        val showCount = !prefs.getBoolean("onboarding_complete", false) || prefs.getBoolean("pref_show_count", true)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = text("reel count", 22, ink, true)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        val badge = text("DAILY RESET", 10, muted, true).apply {
            letterSpacing = .08f
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 30)
            setPadding(dp(13), dp(9), dp(13), dp(9))
        }
        header.addView(badge)
        page.addView(header)

        page.addView(text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())), 14, muted, false).apply {
            setPadding(0, dp(4), 0, dp(19))
        })

        val usageTitle = sectionTitle("App time today", "Automatic foreground time across your social apps")
        page.addView(usageTitle.first)
        val usageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 24)
            setPadding(dp(18), dp(16), dp(18), dp(17))
        }
        usageTotalView = text("0 min", 36, ink, true).apply { includeFontPadding = false }
        usageCard.addView(usageTotalView)
        usageCard.addView(text("TOTAL TIME · TODAY", 10, muted, true).apply {
            letterSpacing = .1f
            setPadding(0, dp(5), 0, dp(13))
        })
        usagePlatformViews.clear()
        SocialUsageTracker.apps.forEachIndexed { index, app ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(text(app.name, 14, ink, true), LinearLayout.LayoutParams(0, dp(38), 1f))
            val value = text("0 min", 14, muted, true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
            usagePlatformViews[app.name] = value
            row.addView(value)
            usageCard.addView(row)
            if (index < SocialUsageTracker.apps.lastIndex) {
                usageCard.addView(View(this).apply { setBackgroundColor(Color.rgb(239, 241, 237)) }, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
        val accessButton = text("Enable automatic tracking", 14, ink, true).apply {
            gravity = Gravity.CENTER
            background = rounded(green, 14)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            tag = "usage_access_button"
            setOnClickListener { openUsageAccessSettings() }
        }
        usageCard.addView(accessButton, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        usageAccessHint = text("Allow Usage Access so Android can record app time while Reel Count is closed. Data stays on this device.", 11, muted, false).apply {
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        usageCard.addView(usageAccessHint)
        page.addView(usageCard)
        refreshUsageViews()

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 26)
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        hero.addView(text("MANUAL REEL TALLY", 12, muted, true).apply { letterSpacing = .12f })
        val countText = if (showCount) total.toString() else "•••"
        val count = text(countText, 76, ink, true).apply {
            setPadding(0, dp(3), 0, 0)
            includeFontPadding = false
        }
        hero.addView(count)
        hero.addView(text("reels logged today", 15, muted, false).apply { setPadding(0, dp(3), 0, dp(20)) })

        val goalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        goalRow.addView(text("Daily goal · edit", 13, ink, true).apply {
            setOnClickListener { showGoalDialog() }
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        goalRow.addView(text(if (showCount) "$total / $goal" else "••• / $goal", 13, ink, true))
        hero.addView(goalRow)
        val track = LinearLayout(this).apply {
            background = rounded(Color.rgb(235, 237, 232), 20)
            clipToOutline = true
        }
        val fill = View(this).apply { background = rounded(ink, 20) }
        track.addView(fill, LinearLayout.LayoutParams(0, dp(8)).apply { weight = if (showCount) (total.toFloat() / goal).coerceAtMost(1f).coerceAtLeast(0.015f) else 0.015f })
        hero.addView(track, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
        hero.addView(text(if (!showCount) "Count display is hidden. You can still log reels." else if (total >= goal) "You reached today's goal." else "${(goal - total).coerceAtLeast(0)} more to reach your goal", 12, muted, false).apply {
            setPadding(0, dp(10), 0, dp(18))
        })
        val add = text("＋   Log 1 reel", 16, ink, true).apply {
            gravity = Gravity.CENTER
            background = rounded(green, 16)
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { changeCount(todayKey, selectedPlatform, 1); render() }
        }
        hero.addView(add, LinearLayout.LayoutParams(-1, -2))
        page.addView(hero)

        page.addView(sectionTitle("Manual reel tally", "Choose a platform before logging a reel"))
        val platforms = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Facebook", "Instagram", "TikTok").forEachIndexed { index, platform ->
            val chip = text(platform, 13, if (platform == selectedPlatform) Color.WHITE else ink, platform == selectedPlatform).apply {
                gravity = Gravity.CENTER
                background = rounded(if (platform == selectedPlatform) ink else Color.WHITE, 24)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { selectedPlatform = platform; render() }
            }
            platforms.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index > 0) leftMargin = dp(8) })
        }
        page.addView(platforms)

        val todayPlatformCounts = listOf("Facebook", "Instagram", "TikTok").map { it to countFor(todayKey, it) }.filter { it.second > 0 }
        if (todayPlatformCounts.isNotEmpty()) {
            val summary = todayPlatformCounts.joinToString("     ·     ") { "${it.first} ${it.second}" }
            page.addView(text(summary, 12, muted, false).apply { setPadding(dp(2), dp(12), 0, 0) })
        }

        val weekHeader = sectionTitle("This week", "Your last 7 days")
        page.addView(weekHeader.first)
        val week = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 24)
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val maxCount = dates.maxOfOrNull { totalFor(it.toString()) }?.coerceAtLeast(1) ?: 1
        dates.forEach { date ->
            val key = date.toString()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val day = if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
            row.addView(text(day, 12, if (date == today) ink else muted, date == today), LinearLayout.LayoutParams(dp(48), dp(43)))
            val barArea = LinearLayout(this).apply { background = rounded(Color.rgb(242, 243, 239), 12); clipToOutline = true }
            val barWidth = (totalFor(key).toFloat() / maxCount).coerceAtLeast(if (totalFor(key) > 0) .04f else 0f)
            val bar = View(this).apply { background = rounded(if (date == today) green else Color.rgb(215, 222, 212), 12) }
            if (totalFor(key) > 0) {
                barArea.addView(bar, LinearLayout.LayoutParams(0, dp(7)).apply { weight = barWidth })
                if (barWidth < 1f) barArea.addView(View(this).apply { setBackgroundColor(Color.TRANSPARENT) }, LinearLayout.LayoutParams(0, dp(7)).apply { weight = 1f - barWidth })
            }
            row.addView(barArea, LinearLayout.LayoutParams(0, dp(7), 1f))
            row.addView(text(totalFor(key).toString(), 12, ink, true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(dp(38), dp(38)))
            week.addView(row)
        }
        page.addView(week)

        val undo = text("Undo last reel", 13, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
            setOnClickListener {
                if (countFor(todayKey, selectedPlatform) > 0) {
                    changeCount(todayKey, selectedPlatform, -1)
                    render()
                } else Toast.makeText(this@MainActivity, "No ${selectedPlatform} reels to undo today", Toast.LENGTH_SHORT).show()
            }
        }
        page.addView(undo)
        page.addView(text("Review your setup", 12, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(8))
            setOnClickListener { onboardingStep = 0; renderOnboarding() }
        })
        page.addView(text("Counts stay on this device. Tap the goal card to change your daily target.", 11, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), 0)
        })
    }

    private fun renderOnboarding() {
        window.statusBarColor = Color.rgb(9, 8, 7)
        window.navigationBarColor = Color.rgb(9, 8, 7)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        val dark = Color.rgb(9, 8, 7)
        val card = Color.rgb(23, 22, 19)
        val orange = Color.rgb(255, 185, 118)
        val scroll = ScrollView(this).apply { setBackgroundColor(dark); isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(18))
            minimumHeight = resources.displayMetrics.heightPixels
        }
        scroll.addView(root, ScrollView.LayoutParams(-1, -1))
        setContentView(scroll)

        val status = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status.addView(text("Reel Count", 12, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(30), 1f))
        status.addView(text("●  ●  ●    ▰", 11, Color.WHITE, true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL })
        root.addView(status)

        val progress = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(5) { index ->
            val segment = View(this).apply { background = rounded(if (index <= onboardingStep) orange else Color.rgb(40, 38, 34), 5) }
            progress.addView(segment, LinearLayout.LayoutParams(0, dp(3), 1f).apply { if (index > 0) leftMargin = dp(5) })
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)).apply { topMargin = dp(10) })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (onboardingStep == 0 || onboardingStep == 4) Gravity.CENTER else Gravity.CENTER_VERTICAL
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(18); bottomMargin = dp(16) })

        when (onboardingStep) {
            0 -> {
                content.addView(text("🧠", 72, Color.WHITE, false).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) })
                content.addView(onboardHeading("Win back\nstudy time", orange).apply { gravity = Gravity.CENTER })
                content.addView(text("Starting today.", 17, Color.rgb(153, 147, 141), false).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(13), 0, 0)
                })
            }
            1, 2 -> {
                val isTime = onboardingStep == 1
                val question = if (isTime) "How much time do you spend on Reels/Shorts daily?" else "How many reels a day feels okay?"
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(text("🧠", 42, Color.WHITE, false), LinearLayout.LayoutParams(dp(60), dp(78)))
                row.addView(text(question, 17, ink, true).apply {
                    background = rounded(Color.rgb(250, 249, 246), 18)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                }, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
                val options = if (isTime) listOf("1–3 hours", "3–5 hours", "5–7 hours", "7+ hours") else listOf(25, 50, 100, 150).map { "$it reels" }
                val selected = if (isTime) onboardingTime else onboardingGoal?.let { "$it reels" }
                options.forEach { option ->
                    val choice = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(card, 15)
                        setPadding(dp(15), 0, dp(15), 0)
                    }
                    val active = option == selected
                    choice.addView(text(if (active) "◉" else "◯", 22, if (active) orange else Color.WHITE, false), LinearLayout.LayoutParams(dp(34), dp(61)))
                    choice.addView(text(option, 16, Color.WHITE, true), LinearLayout.LayoutParams(0, dp(61), 1f))
                    choice.setOnClickListener {
                        if (isTime) onboardingTime = option else onboardingGoal = option.substringBefore(' ').toIntOrNull()
                        renderOnboarding()
                    }
                    content.addView(choice, LinearLayout.LayoutParams(-1, dp(61)).apply { bottomMargin = dp(9) })
                }
            }
            3 -> {
                content.addView(text("🧠🔒", 56, Color.WHITE, false).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) })
                content.addView(onboardHeading("Track app time\nautomatically", Color.WHITE).apply { gravity = Gravity.CENTER })
                val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(12)) }
                chips.addView(text("🔒 On this device", 10, orange, true).apply {
                    background = rounded(card, 30); setPadding(dp(10), dp(8), dp(10), dp(8))
                })
                chips.addView(text("You control access", 10, orange, true).apply {
                    background = rounded(card, 30); setPadding(dp(10), dp(8), dp(10), dp(8))
                }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(7) })
                content.addView(chips)
                val explanation = text("Usage Access lets Android measure how long Facebook, Instagram, and TikTok stay in the foreground, even while Reel Count is closed. It does not identify individual Reels or upload your usage.", 13, Color.WHITE, false).apply {
                    background = rounded(card, 18)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                }
                content.addView(explanation)
                content.addView(text("Grant access in Android Settings now, or skip and enable it from your dashboard later.", 11, Color.rgb(153, 147, 141), false).apply {
                    setPadding(dp(2), dp(12), dp(2), 0)
                })
                content.addView(text(if (SocialUsageTracker.hasAccess(this)) "✓ Usage Access is enabled" else "Open Usage Access settings", 13, orange, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(card, 30)
                    setPadding(dp(14), dp(13), dp(14), dp(13))
                    setOnClickListener { openUsageAccessSettings() }
                    contentDescription = "Open Android Usage Access settings"
                })
            }
            else -> {
                content.addView(text("🧠", 68, Color.WHITE, false).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(18)) })
                content.addView(onboardHeading("Your goal.\nYour pace.", orange).apply { gravity = Gravity.CENTER })
                content.addView(text("A small step is a good place to start.", 15, Color.rgb(153, 147, 141), false).apply {
                    gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(18))
                })
                val summary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(card, 20); setPadding(dp(18), dp(14), dp(18), dp(14)) }
                summary.addView(text("YOUR STARTING PLAN", 10, Color.rgb(170, 164, 156), true).apply { letterSpacing = .1f; setPadding(0, 0, 0, dp(6)) })
                summary.addView(summaryRow("Your daily watch time", onboardingTime ?: "Not set"))
                summary.addView(summaryRow("Your manual reel goal", "${onboardingGoal ?: 50} a day"))
                summary.addView(summaryRow("App-time tracking", if (SocialUsageTracker.hasAccess(this)) "Enabled" else "Enable in Settings"))
                content.addView(summary)
            }
        }

        if (onboardingStep > 0) root.addView(text("Back", 12, Color.rgb(170, 164, 156), false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
            setOnClickListener { onboardingStep = (onboardingStep - 1).coerceAtLeast(0); renderOnboarding() }
        })
        val label = when (onboardingStep) { 0 -> "Let's go"; 4 -> "Open Reel Count"; else -> "Continue" }
        root.addView(text(label, 15, Color.rgb(77, 53, 30), true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(255, 197, 133), 50)
            setPadding(0, dp(17), 0, dp(17))
            setOnClickListener {
                when (onboardingStep) {
                    0 -> { onboardingStep = 1; renderOnboarding() }
                    1 -> if (onboardingTime == null) Toast.makeText(this@MainActivity, "Choose your daily watch time", Toast.LENGTH_SHORT).show() else { onboardingStep = 2; renderOnboarding() }
                    2 -> if (onboardingGoal == null) Toast.makeText(this@MainActivity, "Choose a daily reel goal", Toast.LENGTH_SHORT).show() else { onboardingStep = 3; renderOnboarding() }
                    3 -> { onboardingStep = 4; renderOnboarding() }
                    else -> {
                        val goal = onboardingGoal ?: 50
                        prefs.edit()
                            .putBoolean("onboarding_complete", true)
                            .putString("onboard_time", onboardingTime)
                            .putInt("onboard_goal", goal)
                            .putInt("goal", goal)
                            .apply()
                        render()
                    }
                }
            }
        })
    }

    private fun onboardHeading(value: String, accent: Int) = TextView(this).apply {
        text = value
        textSize = 30f
        setTextColor(accent)
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }

    private fun summaryRow(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(label, 12, Color.WHITE, false), LinearLayout.LayoutParams(0, dp(36), 1f))
        addView(text(value, 12, Color.rgb(255, 189, 125), true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL })
    }

    private fun sectionTitle(title: String, subtitle: String): Pair<LinearLayout, TextView> {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(22), 0, dp(11))
        }
        block.addView(text(title, 17, ink, true))
        val sub = text(subtitle, 12, muted, false).apply { setPadding(0, dp(3), 0, 0) }
        block.addView(sub)
        return block to sub
    }

    private fun showGoalDialog() {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("goal", 60).toString())
            setSelection(text.length)
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }
        AlertDialog.Builder(this)
            .setTitle("Set your daily goal")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val goal = input.text.toString().toIntOrNull()
                if (goal != null && goal > 0) { prefs.edit().putInt("goal", goal).apply(); render() }
                else Toast.makeText(this, "Enter a number above zero", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun changeCount(day: String, platform: String, delta: Int) {
        val key = "${platform.lowercase(Locale.ROOT)}_$day"
        val current = prefs.getInt(key, 0)
        if (current + delta < 0) return
        prefs.edit().putInt(key, current + delta).apply()
    }

    private fun countFor(day: String, platform: String) = prefs.getInt("${platform.lowercase(Locale.ROOT)}_$day", 0)

    private fun totalFor(day: String) = listOf("Facebook", "Instagram", "TikTok").sumOf { countFor(day, it) }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun refreshUsageViews() {
        val hasAccess = SocialUsageTracker.hasAccess(this)
        usageAccessHint?.text = if (hasAccess) {
            "Foreground app time only, not individual Reels. Refreshes every 30 seconds while this dashboard is open."
        } else {
            "Allow Usage Access so Android can record app time while Reel Count is closed. Data stays on this device."
        }
        page.findViewWithTag<TextView>("usage_access_button")?.visibility = if (hasAccess) View.GONE else View.VISIBLE
        if (!hasAccess) return

        val usage = SocialUsageTracker.todayMillis(this)
        usageTotalView?.text = formatDuration(usage.values.sum())
        usagePlatformViews.forEach { (platform, view) -> view.text = formatDuration(usage[platform] ?: 0L) }
    }

    private fun formatDuration(milliseconds: Long): String {
        val minutes = (milliseconds / 60_000L).toInt()
        if (minutes == 0) return if (milliseconds > 0) "<1 min" else "0 min"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) "${hours}h ${remainingMinutes}m" else "$minutes min"
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

