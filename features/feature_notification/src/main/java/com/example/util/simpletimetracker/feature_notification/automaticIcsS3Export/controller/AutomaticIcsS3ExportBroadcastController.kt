package com.example.util.simpletimetracker.feature_notification.automaticIcsS3Export.controller

import com.example.util.simpletimetracker.core.extension.allowDiskRead
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticIcsS3ExportInteractor
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class AutomaticIcsS3ExportBroadcastController @Inject constructor(
    private val automaticIcsS3ExportInteractor: AutomaticIcsS3ExportInteractor,
) {

    suspend fun onReminder() {
        automaticIcsS3ExportInteractor.export()
    }

    fun onFinished() {
        automaticIcsS3ExportInteractor.onFinished()
    }

    fun onBootCompleted() = allowDiskRead { MainScope() }.launch {
        automaticIcsS3ExportInteractor.schedule()
    }
}
