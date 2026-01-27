package com.example.util.simpletimetracker.feature_main.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.util.simpletimetracker.core.base.BaseViewModel
import com.example.util.simpletimetracker.core.extension.allowDiskRead
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.backup.interactor.IcsExportInteractor
import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.notifications.interactor.UpdateExternalViewsInteractor
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.statistics.model.RangeLength
import com.example.util.simpletimetracker.navigation.params.notification.SnackBarParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val externalViewsInteractor: UpdateExternalViewsInteractor,
    private val prefsInteractor: PrefsInteractor,
    private val icsExportInteractor: IcsExportInteractor,
    private val resourceRepo: ResourceRepo,
    private val timeMapper: TimeMapper,
) : BaseViewModel() {

    val initialize: Unit by lazy { syncState() }
    val message: LiveData<SnackBarParams?> = MutableLiveData()
    val isRefreshing: LiveData<Boolean> = MutableLiveData(false)
    val pullToRefreshEnabled: LiveData<Boolean> = MutableLiveData(false)

    private fun syncState() {
        allowDiskRead { viewModelScope }.launch {
            externalViewsInteractor.onAppStart()
        }
        refreshPullToRefreshEnabled()
    }

    fun refreshPullToRefreshEnabled() {
        viewModelScope.launch {
            pullToRefreshEnabled.set(prefsInteractor.getIcsExportS3PullToRefreshEnabled())
        }
    }

    fun onPullToUpload() {
        if (pullToRefreshEnabled.value != true) return
        if (isRefreshing.value == true) return
        isRefreshing.set(true)
        viewModelScope.launch {
            val config = loadS3Config()
                ?: run {
                    isRefreshing.set(false)
                    return@launch
                }
            val range = loadExportRange()
            val result = icsExportInteractor.uploadIcsFileToS3(
                config = config,
                range = range,
            )
            handleResult(result)
            isRefreshing.set(false)
        }
    }

    fun onMessageShown() {
        message.set(null)
    }

    private suspend fun loadS3Config(): S3Config? {
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
            val messageText = resourceRepo.getString(
                com.example.util.simpletimetracker.feature_main.R.string.message_export_s3_settings_incomplete,
            )
            message.set(SnackBarParams(message = messageText))
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

    private suspend fun loadExportRange(): Range? {
        val rangeLength = prefsInteractor.getFileExportRange()
        return if (rangeLength !is RangeLength.All) {
            timeMapper.getRangeStartAndEnd(
                rangeLength = rangeLength,
                shift = 0,
                firstDayOfWeek = prefsInteractor.getFirstDayOfWeek(),
                startOfDayShift = prefsInteractor.getStartOfDayShift(),
            )
        } else {
            null
        }
    }

    private fun handleResult(result: ResultCode) {
        result.message?.let {
            message.set(SnackBarParams(message = it))
        }
    }
}
