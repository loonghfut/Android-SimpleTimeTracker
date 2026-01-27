package com.example.util.simpletimetracker.feature_notification.automaticIcsS3Export.scheduler

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.util.simpletimetracker.core.utils.PendingIntents
import com.example.util.simpletimetracker.feature_notification.core.AlarmManagerController
import com.example.util.simpletimetracker.feature_notification.recevier.NotificationReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AutomaticIcsS3ExportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManagerController: AlarmManagerController,
) {

    fun schedule(durationMillis: Long) {
        val timestamp = System.currentTimeMillis() + durationMillis
        alarmManagerController.scheduleAtTime(timestamp, getPendingIntent())
    }

    fun cancelSchedule() {
        alarmManagerController.cancelSchedule(getPendingIntent())
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_AUTOMATIC_ICS_S3_EXPORT
        }

        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntents.getFlags(),
        )
    }

    companion object {
        private const val REQUEST_CODE = 1
    }
}
