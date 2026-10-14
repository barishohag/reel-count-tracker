package com.reelcount.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.time.LocalDate
import java.time.ZoneId

data class TrackedSocialApp(val name: String, val packages: List<String>)

object SocialUsageTracker {
    val apps = listOf(
        TrackedSocialApp("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
        TrackedSocialApp("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
        TrackedSocialApp("TikTok", listOf("com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill"))
    )

    fun hasAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Returns today's foreground time by platform, including the currently open session. */
    fun todayMillis(context: Context): Map<String, Long> {
        val totals = apps.associate { it.name to 0L }.toMutableMap()
        if (!hasAccess(context)) return totals

        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Include yesterday's events so a session that crossed midnight is clipped at today's start.
        val queryStart = todayStart - 24L * 60 * 60 * 1000
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usage.queryEvents(queryStart, now)
        val appForPackage = apps.flatMap { app -> app.packages.map { it to app.name } }.toMap()
        val activeSince = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        fun close(packageName: String, timestamp: Long) {
            val started = activeSince.remove(packageName) ?: return
            val platform = appForPackage[packageName] ?: return
            val from = maxOf(started, todayStart)
            val until = minOf(timestamp, now)
            if (until > from) totals[platform] = (totals[platform] ?: 0L) + until - from
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName ?: continue
            if (packageName !in appForPackage) continue
            val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            val paused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            }
            if (resumed) activeSince.putIfAbsent(packageName, event.timeStamp)
            if (paused) close(packageName, event.timeStamp)
        }
        activeSince.keys.toList().forEach { close(it, now) }
        return totals
    }
}

