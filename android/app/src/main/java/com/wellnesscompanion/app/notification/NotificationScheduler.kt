package com.wellnesscompanion.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    /** Schedule all recurring wellness reminders */
    fun scheduleAll(context: Context) {
        scheduleHydrationReminders(context)
        scheduleMealReminders(context)
        scheduleEveningCheckIn(context)
    }

    /** Cancel all recurring reminders */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(HydrationReminderWorker.WORK_TAG)
        wm.cancelUniqueWork(MealReminderWorker.WORK_TAG)
        wm.cancelUniqueWork(EveningCheckInWorker.WORK_TAG)
    }

    /** Every 2 hours during waking hours (8am–10pm) */
    fun scheduleHydrationReminders(context: Context) {
        val request = PeriodicWorkRequestBuilder<HydrationReminderWorker>(2, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes(8, 0), TimeUnit.MINUTES)
            .addTag(HydrationReminderWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HydrationReminderWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** 3x daily around mealtimes (8am, 12pm, 6pm) — uses a single 8-hour periodic */
    fun scheduleMealReminders(context: Context) {
        val request = PeriodicWorkRequestBuilder<MealReminderWorker>(8, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes(8, 0), TimeUnit.MINUTES)
            .addTag(MealReminderWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MealReminderWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Once daily at 9pm — evening reflection prompt */
    fun scheduleEveningCheckIn(context: Context) {
        val request = PeriodicWorkRequestBuilder<EveningCheckInWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes(21, 0), TimeUnit.MINUTES)
            .addTag(EveningCheckInWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EveningCheckInWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Calculate minutes until the next occurrence of [targetHour]:[targetMin] */
    private fun initialDelayMinutes(targetHour: Int, targetMin: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis) / 60_000
    }
}
