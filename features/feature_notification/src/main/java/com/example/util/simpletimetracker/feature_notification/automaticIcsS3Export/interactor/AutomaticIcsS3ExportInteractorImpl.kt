package com.example.util.simpletimetracker.feature_notification.automaticIcsS3Export.interactor

import com.example.util.simpletimetracker.core.extension.post
import com.example.util.simpletimetracker.core.repo.AutomaticIcsS3ExportRepo
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticIcsS3ExportInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.IcsExportInteractor
import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.feature_notification.automaticIcsS3Export.scheduler.AutomaticIcsS3ExportScheduler
import com.example.util.simpletimetracker.feature_notification.core.GetTimeLeftToTimestampInteractor
import javax.inject.Inject

class AutomaticIcsS3ExportInteractorImpl @Inject constructor(
    private val scheduler: AutomaticIcsS3ExportScheduler,
    private val icsExportInteractor: IcsExportInteractor,
    private val prefsInteractor: PrefsInteractor,
    private val automaticIcsS3ExportRepo: AutomaticIcsS3ExportRepo,
    private val getTimeLeftToTimestampInteractor: GetTimeLeftToTimestampInteractor,
) : AutomaticIcsS3ExportInteractor {

    override suspend fun schedule() {
        if (!prefsInteractor.getIcsExportS3AutomaticEnabled()) return
        val triggerTime = prefsInteractor.getIcsExportS3AutomaticTriggerTime()
        val timestamp = getTimeLeftToTimestampInteractor.execute(triggerTime)
        scheduler.schedule(timestamp)
    }

    override fun cancel() {
        scheduler.cancelSchedule()
    }

    override fun onFinished() {
        automaticIcsS3ExportRepo.inProgress.post(false)
    }

    override suspend fun export(): ResultCode? {
        automaticIcsS3ExportRepo.inProgress.post(true)

        if (!prefsInteractor.getIcsExportS3AutomaticEnabled()) {
            onFinished()
            return null
        }

        val config = buildS3Config()
            ?: run {
                prefsInteractor.setIcsExportS3AutomaticError(true)
                prefsInteractor.setIcsExportS3AutomaticEnabled(false)
                cancel()
                onFinished()
                return null
            }

        val result = icsExportInteractor.uploadIcsFileToS3(
            config = config,
            range = null,
        )

        if (result is ResultCode.Success) {
            schedule()
            prefsInteractor.setIcsExportS3AutomaticLastSaveTime(System.currentTimeMillis())
        } else {
            cancel()
            prefsInteractor.setIcsExportS3AutomaticError(true)
            prefsInteractor.setIcsExportS3AutomaticEnabled(false)
        }

        onFinished()

        return result
    }

    private suspend fun buildS3Config(): S3Config? {
        val endpoint = prefsInteractor.getIcsExportS3Endpoint().trim()
        val accessKey = prefsInteractor.getIcsExportS3AccessKey().trim()
        val secretKey = prefsInteractor.getIcsExportS3SecretKey().trim()
        val bucket = prefsInteractor.getIcsExportS3Bucket().trim()
        val region = prefsInteractor.getIcsExportS3Region().trim()
        val timeoutSeconds = prefsInteractor.getIcsExportS3TimeoutSeconds()
        val addressing = prefsInteractor.getIcsExportS3Addressing()
        val tlsVerify = prefsInteractor.getIcsExportS3TlsVerify()
        val objectKeyTemplate = prefsInteractor.getIcsExportS3ObjectKeyTemplate().trim()

        if (endpoint.isEmpty() || accessKey.isEmpty() || secretKey.isEmpty() ||
            bucket.isEmpty() || region.isEmpty() || timeoutSeconds <= 0
        ) {
            return null
        }

        return S3Config(
            endpoint = endpoint,
            accessKey = accessKey,
            secretKey = secretKey,
            bucket = bucket,
            region = region,
            timeoutSeconds = timeoutSeconds,
            addressing = addressing,
            tlsVerify = tlsVerify,
            objectKeyTemplate = objectKeyTemplate,
        )
    }
}
