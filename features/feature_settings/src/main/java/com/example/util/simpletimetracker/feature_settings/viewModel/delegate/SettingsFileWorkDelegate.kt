package com.example.util.simpletimetracker.feature_settings.viewModel.delegate

import androidx.annotation.VisibleForTesting
import com.example.util.simpletimetracker.core.R
import com.example.util.simpletimetracker.core.base.ViewModelDelegate
import com.example.util.simpletimetracker.core.extension.fromHtml
import com.example.util.simpletimetracker.core.extension.set
import com.example.util.simpletimetracker.core.extension.toModel
import com.example.util.simpletimetracker.core.extension.toParams
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.core.repo.FileWorkRepo
import com.example.util.simpletimetracker.core.repo.PermissionRepo
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticBackupInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticExportInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticIcsS3ExportInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.BackupInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.CsvExportInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.IcsExportInteractor
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.statistics.interactor.SettingsDataUpdateInteractor
import com.example.util.simpletimetracker.domain.backup.model.BackupOptionsData
import com.example.util.simpletimetracker.domain.backup.model.PartialBackupRestoreData
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.statistics.model.RangeLength
import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.fileExport.ExportDateTimeFormat
import com.example.util.simpletimetracker.navigation.RequestCode
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.action.ActionParams
import com.example.util.simpletimetracker.navigation.params.action.CreateFileParams
import com.example.util.simpletimetracker.navigation.params.action.OpenFileParams
import com.example.util.simpletimetracker.navigation.params.action.ShareFileParams
import com.example.util.simpletimetracker.navigation.params.notification.SnackBarParams
import com.example.util.simpletimetracker.navigation.params.screen.DataExportSettingDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.DataExportSettingsResult
import com.example.util.simpletimetracker.navigation.params.screen.HelpDialogParams
import com.example.util.simpletimetracker.navigation.params.screen.PartialRestoreParams
import com.example.util.simpletimetracker.navigation.params.screen.RangeLengthParams
import com.example.util.simpletimetracker.navigation.params.screen.StandardDialogParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsFileWorkDelegate @Inject constructor(
    private val timeMapper: TimeMapper,
    private val fileWorkRepo: FileWorkRepo,
    private val resourceRepo: ResourceRepo,
    private val permissionRepo: PermissionRepo,
    private val router: Router,
    private val backupInteractor: BackupInteractor,
    private val csvExportInteractor: CsvExportInteractor,
    private val icsExportInteractor: IcsExportInteractor,
    private val prefsInteractor: PrefsInteractor,
    private val automaticBackupInteractor: AutomaticBackupInteractor,
    private val automaticExportInteractor: AutomaticExportInteractor,
    private val automaticIcsS3ExportInteractor: AutomaticIcsS3ExportInteractor,
    private val settingsDataUpdateInteractor: SettingsDataUpdateInteractor,
) : ViewModelDelegate() {

    var partialBackupRestoreData: PartialBackupRestoreData? = null
    var partialBackupRestoreDataSelectable: PartialBackupRestoreData? = null
    private var saveOptionsData: BackupOptionsData.Save? = null
    private var restoreOptionsData: BackupOptionsData.Restore? = null

    fun onAppVisible() {
        delegateScope.launch {
            checkForAutomaticBackupError()
        }
    }

    fun onRestoreConfirmed() {
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_OPEN_FILE,
            work = ::onRestoreBackup,
            params = OpenFileParams(
                type = FILE_TYPE_BIN_OPEN,
                notHandledCallback = ::onFileOpenError,
            ),
        )
    }

    fun onPartialRestoreClick() {
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_OPEN_FILE,
            work = ::onPartialRestore,
            params = OpenFileParams(
                type = FILE_TYPE_BIN_OPEN,
                notHandledCallback = ::onFileOpenError,
            ),
        )
    }

    fun onPartialRestoreConfirmed(
        data: PartialBackupRestoreData,
    ) {
        onPartialRestoreBackup(data)
    }

    fun onCsvImportConfirmed() {
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_OPEN_FILE,
            work = ::onImportCsvFile,
            params = OpenFileParams(
                type = FILE_TYPE_CSV_OPEN,
                notHandledCallback = ::onFileOpenError,
            ),
        )
    }

    fun onCsvExport(
        data: DataExportSettingsResult,
    ) = delegateScope.launch {
        val range = mapDataExportSettingsRange(data.range)
        prefsInteractor.setFileExportRange(data.range.toModel())
        // Don't save if it is default.
        data.customFileName
            .takeIf { it != CSV_EXPORT_DEFAULT_FILE_NAME }
            .orEmpty()
            .let { prefsInteractor.setCsvExportCustomFileName(it) }
        val dateTimeFormat = prefsInteractor.getCsvExportDateTimeFormat()
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_CREATE_FILE,
            work = { onSaveCsvFile(it, range, dateTimeFormat) },
            params = CreateFileParams(
                fileName = data.customFileName
                    .let(::insertDateTimeIntoFileName),
                type = FILE_TYPE_CSV,
                notHandledCallback = ::onFileCreateError,
            ),
        )
    }

    fun onIcsExport(
        data: DataExportSettingsResult,
    ) = delegateScope.launch {
        val range = mapDataExportSettingsRange(data.range)
        prefsInteractor.setFileExportRange(data.range.toModel())
        // Don't save if it is default.
        data.customFileName
            .takeIf { it != ICS_EXPORT_DEFAULT_FILE_NAME }
            .orEmpty()
            .let { prefsInteractor.setIcsExportCustomFileName(it) }
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_CREATE_FILE,
            work = { onSaveIcsFile(it, range) },
            params = CreateFileParams(
                fileName = data.customFileName
                    .let(::insertDateTimeIntoFileName),
                type = FILE_TYPE_ICS,
                notHandledCallback = ::onFileCreateError,
            ),
        )
    }

    fun onFileWork() {
        delegateScope.launch {
            checkForAutomaticBackupError()
            requestScreenUpdate()
        }
    }

    fun onSaveClick(params: BackupOptionsData.Save) {
        saveOptionsData = params
        requestFileWork(
            requestCode = RequestCode.REQUEST_CODE_CREATE_FILE,
            work = ::onSaveBackup,
            params = CreateFileParams(
                fileName = "stt_${getFileNameTimeStamp()}.backup",
                type = FILE_TYPE_BIN,
                notHandledCallback = ::onFileCreateError,
            ),
        )
    }

    fun onAutomaticBackupClick() = delegateScope.launch {
        if (loadAutomaticBackupEnabled()) {
            disableAutomaticBackup()
        } else {
            requestFileWork(
                requestCode = RequestCode.REQUEST_CODE_CREATE_FILE,
                work = ::onAutomaticBackup,
                params = CreateFileParams(
                    fileName = "stt_automatic.backup",
                    type = FILE_TYPE_BIN,
                    notHandledCallback = ::onFileCreateError,
                ),
            )
        }
    }

    fun onAutomaticExportClick() = delegateScope.launch {
        if (loadAutomaticExportEnabled()) {
            disableAutomaticExport()
        } else {
            requestFileWork(
                requestCode = RequestCode.REQUEST_CODE_CREATE_FILE,
                work = ::onAutomaticExport,
                params = CreateFileParams(
                    fileName = "stt_records_automatic.csv",
                    type = FILE_TYPE_CSV,
                    notHandledCallback = ::onFileCreateError,
                ),
            )
        }
    }

    fun onAutomaticIcsS3UploadClick() = delegateScope.launch {
        if (loadAutomaticIcsS3UploadEnabled()) {
            disableAutomaticIcsS3Upload()
        } else {
            if (loadS3Config() == null) return@launch
            prefsInteractor.setIcsExportS3AutomaticEnabled(true)
            prefsInteractor.setIcsExportS3AutomaticError(false)
            automaticIcsS3ExportInteractor.schedule()
            requestScreenUpdate()
        }
    }

    fun onTriggerAutoExportClick() {
        delegateScope.launch {
            val result = automaticExportInteractor.export()
            result?.message?.let(::showMessage)
        }
    }

    fun onRestoreClick(tag: String, params: BackupOptionsData.Restore) {
        restoreOptionsData = params
        router.navigate(
            StandardDialogParams(
                tag = tag,
                message = resourceRepo.getString(R.string.settings_dialog_message),
                btnPositive = resourceRepo.getString(R.string.ok),
                btnNegative = resourceRepo.getString(R.string.cancel),
            ),
        )
    }

    fun onExportCsvClick(tag: String) = delegateScope.launch {
        DataExportSettingDialogParams(
            tag = tag,
            selectedRange = prefsInteractor.getFileExportRange().toParams(),
            defaultFileName = CSV_EXPORT_DEFAULT_FILE_NAME,
            customFileName = prefsInteractor.getCsvExportCustomFileName(),
        ).let(router::navigate)
    }

    fun onImportCsvClick(tag: String) {
        router.navigate(
            StandardDialogParams(
                tag = tag,
                message = resourceRepo.getString(R.string.archive_deletion_alert),
                btnPositive = resourceRepo.getString(R.string.ok),
                btnNegative = resourceRepo.getString(R.string.cancel),
            ),
        )
    }

    fun onImportCsvHelpClick() {
        HelpDialogParams(
            title = resourceRepo.getString(R.string.settings_import_csv),
            text = resourceRepo.getString(R.string.settings_import_csv_help).fromHtml(),
        ).let(router::navigate)
    }

    fun onExportIcsClick(tag: String) = delegateScope.launch {
        DataExportSettingDialogParams(
            tag = tag,
            selectedRange = prefsInteractor.getFileExportRange().toParams(),
            defaultFileName = ICS_EXPORT_DEFAULT_FILE_NAME,
            customFileName = prefsInteractor.getIcsExportCustomFileName(),
        ).let(router::navigate)
    }

    fun onIcsExportToS3() = delegateScope.launch {
        val config = loadS3Config() ?: return@launch
        val range = mapDataExportSettingsRange(
            prefsInteractor.getFileExportRange().toParams(),
        )
        executeFileWork {
            icsExportInteractor.uploadIcsFileToS3(
                config = config,
                range = range,
            )
        }
    }

    private suspend fun mapDataExportSettingsRange(
        data: RangeLengthParams,
    ): Range? {
        val rangeLength = data.toModel()
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

    private suspend fun checkForAutomaticBackupError() {
        val automaticBackupError = prefsInteractor.getAutomaticBackupError()
        val automaticExportError = prefsInteractor.getAutomaticExportError()
        val automaticIcsS3Error = prefsInteractor.getIcsExportS3AutomaticError()

        if (automaticBackupError || automaticExportError || automaticIcsS3Error) {
            val backupString = resourceRepo.getString(R.string.message_automatic_backup_error)
                .takeIf { automaticBackupError }
            val exportString = resourceRepo.getString(R.string.message_automatic_export_error)
                .takeIf { automaticExportError }
            val icsS3String = resourceRepo.getString(R.string.message_automatic_ics_s3_export_error)
                .takeIf { automaticIcsS3Error }
            val hint = resourceRepo.getString(R.string.message_automatic_error_hint)
            val message = listOfNotNull(backupString, exportString, icsS3String, hint)
                .joinToString(separator = " ")

            router.show(
                SnackBarParams(
                    message = message,
                    duration = SnackBarParams.Duration.Indefinite,
                ),
            )
        }

        if (automaticBackupError) prefsInteractor.setAutomaticBackupError(false)
        if (automaticExportError) prefsInteractor.setAutomaticExportError(false)
        if (automaticIcsS3Error) prefsInteractor.setIcsExportS3AutomaticError(false)
    }

    private fun onSaveBackup(uriString: String?) {
        if (uriString == null) return
        val params = saveOptionsData ?: return
        executeFileWork(shareUriString = uriString) {
            backupInteractor.saveBackupFile(uriString, params)
        }
    }

    private fun onAutomaticBackup(uriString: String?) {
        delegateScope.launch {
            if (uriString == null) {
                requestScreenUpdate()
                return@launch
            }

            val exportUri = prefsInteractor.getAutomaticExportUri()
            permissionRepo.releasePersistableUriPermissions(exportUri)

            if (permissionRepo.takePersistableUriPermission(uriString)) {
                prefsInteractor.setAutomaticBackupUri(uriString)
                automaticBackupInteractor.schedule()
            } else {
                prefsInteractor.setAutomaticBackupUri("")
                onFileCreateError()
            }

            requestScreenUpdate()
        }
    }

    private fun onAutomaticExport(uriString: String?) {
        delegateScope.launch {
            if (uriString == null) {
                requestScreenUpdate()
                return@launch
            }

            val backupUri = prefsInteractor.getAutomaticBackupUri()
            permissionRepo.releasePersistableUriPermissions(backupUri)

            if (permissionRepo.takePersistableUriPermission(uriString)) {
                prefsInteractor.setAutomaticExportUri(uriString)
                automaticExportInteractor.schedule()
            } else {
                prefsInteractor.setAutomaticExportUri("")
                onFileCreateError()
            }

            requestScreenUpdate()
        }
    }

    private fun disableAutomaticBackup() {
        delegateScope.launch {
            prefsInteractor.setAutomaticBackupUri("")
            prefsInteractor.setAutomaticBackupError(false)
            automaticBackupInteractor.cancel()
            requestScreenUpdate()
        }
    }

    private fun disableAutomaticExport() {
        delegateScope.launch {
            prefsInteractor.setAutomaticExportUri("")
            prefsInteractor.setAutomaticExportError(false)
            automaticExportInteractor.cancel()
            requestScreenUpdate()
        }
    }

    private fun disableAutomaticIcsS3Upload() {
        delegateScope.launch {
            prefsInteractor.setIcsExportS3AutomaticEnabled(false)
            prefsInteractor.setIcsExportS3AutomaticError(false)
            automaticIcsS3ExportInteractor.cancel()
            requestScreenUpdate()
        }
    }

    private fun onRestoreBackup(uriString: String?) {
        if (uriString == null) return
        val params = restoreOptionsData ?: return
        val restoreSettings = when (params) {
            is BackupOptionsData.Restore.Standard -> false
            is BackupOptionsData.Restore.WithSettings -> true
        }
        executeFileWork(
            doAfter = { if (restoreSettings) restartApp() },
        ) {
            backupInteractor.restoreBackupFile(uriString, params)
        }
    }

    private fun onPartialRestoreBackup(
        data: PartialBackupRestoreData,
    ) {
        val params = BackupOptionsData.Custom(data)
        executeFileWork {
            backupInteractor.partialRestoreBackupFile(params)
        }
    }

    private fun onPartialRestore(uriString: String?) {
        if (uriString == null) return
        executeFileWork(
            doAfter = { router.navigate(PartialRestoreParams) },
        ) {
            val (result, data) = backupInteractor.readBackupFileContent(uriString)
            partialBackupRestoreData = data
            result
        }
    }

    private fun onSaveCsvFile(
        uriString: String?,
        range: Range?,
        dateTimeFormat: ExportDateTimeFormat,
    ) {
        if (uriString == null) return
        executeFileWork(shareUriString = uriString) {
            csvExportInteractor.saveCsvFile(
                uriString = uriString,
                range = range,
                dateTimeFormat = dateTimeFormat,
            )
        }
    }

    private fun onImportCsvFile(uriString: String?) {
        if (uriString == null) return
        executeFileWork {
            csvExportInteractor.importCsvFile(uriString)
        }
    }

    private fun onSaveIcsFile(
        uriString: String?,
        range: Range?,
    ) {
        if (uriString == null) return
        executeFileWork(shareUriString = uriString) {
            icsExportInteractor.saveIcsFile(
                uriString = uriString,
                range = range,
            )
        }
    }

    private fun requestFileWork(
        requestCode: String,
        work: (uriString: String?) -> Unit,
        params: ActionParams,
    ) {
        router.setResultListener(requestCode) { result ->
            work(result as? String)
        }
        router.execute(params)
    }

    // Need global scope or not cancelable scope.
    // Otherwise process will be stopped on navigation.
    private fun executeFileWork(
        shareUriString: String? = null,
        doAfter: suspend () -> Unit = {},
        doWork: suspend () -> ResultCode,
    ) = delegateScope.launch {
        fileWorkRepo.inProgress.set(true)

        val resultCode = doWork()
        val isSuccessful = resultCode is ResultCode.Success
        resultCode.message?.let {
            showMessage(
                string = it,
                shareUriString = shareUriString.takeIf { isSuccessful },
            )
        }

        fileWorkRepo.inProgress.set(false)

        doAfter()
    }

    private fun onFileOpenError() {
        showMessage(resourceRepo.getString(R.string.settings_file_open_error))
    }

    private fun onFileCreateError() {
        showMessage(resourceRepo.getString(R.string.settings_file_create_error))
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
            showMessage(resourceRepo.getString(R.string.message_export_s3_settings_incomplete))
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

    fun showMessage(
        string: String,
        shareUriString: String? = null,
    ) {
        val isForSharing = shareUriString != null
        val actionText = if (isForSharing) {
            resourceRepo.getString(R.string.message_action_share)
        } else {
            ""
        }
        val params = SnackBarParams(
            message = string,
            actionText = actionText,
            actionListener = { onShareClicked(shareUriString) },
        )
        router.show(params)
    }

    private fun onShareClicked(
        shareUriString: String?,
    ) {
        val shareData = ShareFileParams(
            uriString = shareUriString.orEmpty(),
            type = FILE_TYPE_BIN_OPEN,
            notHandledCallback = {
                resourceRepo.getString(R.string.message_app_not_found)
                    .let(::showMessage)
            },
        )
        router.execute(shareData)
    }

    private fun insertDateTimeIntoFileName(
        fileName: String,
    ): String {
        return fileName.replace("{$FILE_EXPORT_DATE_TAG}", getFileNameTimeStamp())
    }

    private fun getFileNameTimeStamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private suspend fun loadAutomaticBackupEnabled(): Boolean {
        return prefsInteractor.getAutomaticBackupUri().isNotEmpty()
    }

    private suspend fun loadAutomaticExportEnabled(): Boolean {
        return prefsInteractor.getAutomaticExportUri().isNotEmpty()
    }

    private suspend fun loadAutomaticIcsS3UploadEnabled(): Boolean {
        return prefsInteractor.getIcsExportS3AutomaticEnabled()
    }

    private suspend fun requestScreenUpdate() {
        settingsDataUpdateInteractor.send()
    }

    private suspend fun restartApp() {
        if (restartAppIsBlocked) return
        // Delay for message to show.
        delay(1000)
        router.restartApp()
    }

    companion object {
        @VisibleForTesting
        var restartAppIsBlocked: Boolean = false

        private const val FILE_EXPORT_DATE_TAG = "date"
        private const val CSV_EXPORT_DEFAULT_FILE_NAME = "stt_records_{$FILE_EXPORT_DATE_TAG}.csv"
        private const val ICS_EXPORT_DEFAULT_FILE_NAME = "stt_events_{$FILE_EXPORT_DATE_TAG}.ics"
        private const val FILE_TYPE_BIN = "application/x-binary"
        private const val FILE_TYPE_BIN_OPEN = "application/*"
        private const val FILE_TYPE_CSV = "text/csv"
        private const val FILE_TYPE_CSV_OPEN = "text/*"
        private const val FILE_TYPE_ICS = "application/ics"
    }
}