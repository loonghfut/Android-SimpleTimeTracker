package com.example.util.simpletimetracker.feature_settings.viewModel.delegate

import android.text.InputType
import com.example.util.simpletimetracker.core.base.ViewModelDelegate
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticBackupInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticExportInteractor
import com.example.util.simpletimetracker.domain.backup.interactor.AutomaticIcsS3ExportInteractor
import com.example.util.simpletimetracker.domain.backup.model.S3Addressing
import com.example.util.simpletimetracker.domain.extension.flip
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.backup.model.BackupOptionsData
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_settings.R
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.interactor.SettingsAdvancedOptionsUpdateInteractor
import com.example.util.simpletimetracker.feature_settings.interactor.SettingsBackupViewDataInteractor
import com.example.util.simpletimetracker.feature_settings.mapper.SettingsMapper
import com.example.util.simpletimetracker.feature_settings.model.S3AddressingOption
import com.example.util.simpletimetracker.feature_settings.viewModel.SettingsViewModel
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.screen.BackupOptionsParams
import com.example.util.simpletimetracker.navigation.params.screen.DataExportSettingsResult
import com.example.util.simpletimetracker.navigation.params.screen.OptionsListParams
import com.example.util.simpletimetracker.navigation.params.screen.TextInputDialogParams
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

class SettingsBackupViewModelDelegate @Inject constructor(
    private val router: Router,
    private val resourceRepo: ResourceRepo,
    private val settingsBackupViewDataInteractor: SettingsBackupViewDataInteractor,
    private val settingsFileWorkDelegate: SettingsFileWorkDelegate,
    private val prefsInteractor: PrefsInteractor,
    private val settingsMapper: SettingsMapper,
    private val automaticBackupInteractor: AutomaticBackupInteractor,
    private val automaticExportInteractor: AutomaticExportInteractor,
    private val automaticIcsS3ExportInteractor: AutomaticIcsS3ExportInteractor,
    private val settingsAdvancedOptionsUpdateInteractor: SettingsAdvancedOptionsUpdateInteractor,
) : ViewModelDelegate() {

    private var parent: SettingsParent? = null
    private var isCollapsed: Boolean = true

    fun init(parent: SettingsParent) {
        this.parent = parent
    }

    suspend fun getViewData(): List<ViewHolderType> {
        return settingsBackupViewDataInteractor.execute(
            isCollapsed = isCollapsed,
        )
    }

    fun onBlockClicked(block: SettingsBlock) {
        when (block) {
            SettingsBlock.BackupCollapse ->
                onCollapseClick()
            SettingsBlock.BackupSave ->
                settingsFileWorkDelegate.onSaveClick(
                    params = BackupOptionsData.Save.Standard,
                )
            SettingsBlock.BackupAutomatic ->
                settingsFileWorkDelegate.onAutomaticBackupClick()
            SettingsBlock.BackupAutomaticTime ->
                onAutoBackupTriggerTimeClicked()
            SettingsBlock.BackupRestore ->
                settingsFileWorkDelegate.onRestoreClick(
                    tag = BACKUP_RESTORE_DIALOG_TAG,
                    params = BackupOptionsData.Restore.Standard,
                )
            SettingsBlock.BackupCustomized ->
                onCustomizeClick()
            // TODO move to export delegate?
            SettingsBlock.ExportSpreadsheet ->
                settingsFileWorkDelegate.onExportCsvClick(CSV_EXPORT_DIALOG_TAG)
            SettingsBlock.ExportSpreadsheetAutomatic ->
                settingsFileWorkDelegate.onAutomaticExportClick()
            SettingsBlock.ExportSpreadsheetAutomaticTime ->
                onAutoExportTriggerTimeClicked()
            SettingsBlock.ExportSpreadsheetImport ->
                settingsFileWorkDelegate.onImportCsvClick(CSV_IMPORT_ALERT_DIALOG_TAG)
            SettingsBlock.ExportSpreadsheetImportHint ->
                settingsFileWorkDelegate.onImportCsvHelpClick()
            SettingsBlock.ExportIcs -> delegateScope.launch {
                settingsAdvancedOptionsUpdateInteractor.sendDismiss()
                delay(200)
                settingsFileWorkDelegate.onExportIcsClick(ICS_EXPORT_DIALOG_TAG)
            }
            SettingsBlock.ExportIcsS3Endpoint -> onS3EndpointClick()
            SettingsBlock.ExportIcsS3AccessKey -> onS3AccessKeyClick()
            SettingsBlock.ExportIcsS3SecretKey -> onS3SecretKeyClick()
            SettingsBlock.ExportIcsS3Bucket -> onS3BucketClick()
            SettingsBlock.ExportIcsS3Region -> onS3RegionClick()
            SettingsBlock.ExportIcsS3Timeout -> onS3TimeoutClick()
            SettingsBlock.ExportIcsS3Addressing -> onS3AddressingClick()
            SettingsBlock.ExportIcsS3TlsVerify -> onS3TlsVerifyClick()
            SettingsBlock.ExportIcsS3ObjectKey -> onS3ObjectKeyClick()
            SettingsBlock.ExportIcsS3Upload -> onExportIcsS3Click()
            SettingsBlock.ExportIcsS3Automatic -> settingsFileWorkDelegate.onAutomaticIcsS3UploadClick()
            SettingsBlock.ExportIcsS3AutomaticTime -> onAutoIcsS3UploadTriggerTimeClicked()
            else -> {
                // Do nothing
            }
        }
    }

    fun onDateTimeSet(timestamp: Long, tag: String?) {
        onDateTimeSetDelegate(timestamp, tag)
    }

    fun onPositiveClick(tag: String?) {
        when (tag) {
            BACKUP_RESTORE_DIALOG_TAG -> {
                settingsFileWorkDelegate.onRestoreConfirmed()
            }
            CSV_IMPORT_ALERT_DIALOG_TAG -> delegateScope.launch {
                settingsAdvancedOptionsUpdateInteractor.sendDismiss()
                settingsFileWorkDelegate.onCsvImportConfirmed()
            }
        }
    }

    fun onDataExportSettingsSelected(data: DataExportSettingsResult) {
        when (data.tag) {
            CSV_EXPORT_DIALOG_TAG -> settingsFileWorkDelegate.onCsvExport(data)
            ICS_EXPORT_DIALOG_TAG -> settingsFileWorkDelegate.onIcsExport(data)
        }
    }

    fun onTextInputConfirmed(text: String, tag: String?) {
        delegateScope.launch {
            when (tag) {
                ICS_EXPORT_S3_ENDPOINT_DIALOG_TAG -> prefsInteractor.setIcsExportS3Endpoint(text)
                ICS_EXPORT_S3_ACCESS_KEY_DIALOG_TAG -> prefsInteractor.setIcsExportS3AccessKey(text)
                ICS_EXPORT_S3_SECRET_KEY_DIALOG_TAG -> prefsInteractor.setIcsExportS3SecretKey(text)
                ICS_EXPORT_S3_BUCKET_DIALOG_TAG -> prefsInteractor.setIcsExportS3Bucket(text)
                ICS_EXPORT_S3_REGION_DIALOG_TAG -> prefsInteractor.setIcsExportS3Region(text)
                ICS_EXPORT_S3_TIMEOUT_DIALOG_TAG -> {
                    val value = text.trim().toIntOrNull() ?: 0
                    prefsInteractor.setIcsExportS3TimeoutSeconds(value)
                }
                ICS_EXPORT_S3_OBJECT_KEY_DIALOG_TAG ->
                    prefsInteractor.setIcsExportS3ObjectKeyTemplate(text)
                else -> return@launch
            }
            parent?.updateContent()
        }
    }

    fun onOptionsItemClick(id: OptionsListParams.Item.Id) {
        if (id !is S3AddressingOption) return
        delegateScope.launch {
            val addressing = when (id) {
                S3AddressingOption.Path -> S3Addressing.Path
                S3AddressingOption.VirtualHost -> S3Addressing.VirtualHost
            }
            prefsInteractor.setIcsExportS3Addressing(addressing)
            parent?.updateContent()
        }
    }

    fun collapse() {
        isCollapsed = true
    }

    private fun onAutoBackupTriggerTimeClicked() {
        delegateScope.launch {
            parent?.openDateTimeDialog(
                tag = SettingsViewModel.AUTO_BACKUP_TRIGGER_TIME_DIALOG_TAG,
                timestamp = prefsInteractor.getAutomaticBackupTriggerTime(),
                useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
            )
        }
    }

    private fun onAutoExportTriggerTimeClicked() {
        delegateScope.launch {
            parent?.openDateTimeDialog(
                tag = SettingsViewModel.AUTO_EXPORT_TRIGGER_TIME_DIALOG_TAG,
                timestamp = prefsInteractor.getAutomaticExportTriggerTime(),
                useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
            )
        }
    }

    private fun onAutoIcsS3UploadTriggerTimeClicked() {
        delegateScope.launch {
            parent?.openDateTimeDialog(
                tag = SettingsViewModel.AUTO_ICS_S3_EXPORT_TRIGGER_TIME_DIALOG_TAG,
                timestamp = prefsInteractor.getIcsExportS3AutomaticTriggerTime(),
                useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
            )
        }
    }

    private fun onDateTimeSetDelegate(timestamp: Long, tag: String?) = delegateScope.launch {
        when (tag) {
            SettingsViewModel.AUTO_BACKUP_TRIGGER_TIME_DIALOG_TAG -> {
                val newValue = settingsMapper.toStartOfDayShift(timestamp, wasPositive = true)
                prefsInteractor.setAutomaticBackupTriggerTime(newValue)
                automaticBackupInteractor.schedule()
                parent?.updateContent()
            }
            SettingsViewModel.AUTO_EXPORT_TRIGGER_TIME_DIALOG_TAG -> {
                val newValue = settingsMapper.toStartOfDayShift(timestamp, wasPositive = true)
                prefsInteractor.setAutomaticExportTriggerTime(newValue)
                automaticExportInteractor.schedule()
                parent?.updateContent()
            }
            SettingsViewModel.AUTO_ICS_S3_EXPORT_TRIGGER_TIME_DIALOG_TAG -> {
                val newValue = settingsMapper.toStartOfDayShift(timestamp, wasPositive = true)
                prefsInteractor.setIcsExportS3AutomaticTriggerTime(newValue)
                automaticIcsS3ExportInteractor.schedule()
                parent?.updateContent()
            }
        }
    }

    private fun onCustomizeClick() {
        router.navigate(BackupOptionsParams)
    }

    private fun onExportIcsS3Click() = delegateScope.launch {
        settingsAdvancedOptionsUpdateInteractor.sendDismiss()
        delay(200)
        settingsFileWorkDelegate.onIcsExportToS3()
    }

    private fun onS3EndpointClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_ENDPOINT_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_endpoint,
            hint = R.string.settings_export_ics_s3_endpoint_hint,
            value = prefsInteractor.getIcsExportS3Endpoint(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_URI or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
    }

    private fun onS3AccessKeyClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_ACCESS_KEY_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_access_key,
            hint = R.string.settings_export_ics_s3_access_key_hint,
            value = prefsInteractor.getIcsExportS3AccessKey(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
    }

    private fun onS3SecretKeyClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_SECRET_KEY_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_secret_key,
            hint = R.string.settings_export_ics_s3_secret_key_hint,
            value = prefsInteractor.getIcsExportS3SecretKey(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
    }

    private fun onS3BucketClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_BUCKET_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_bucket,
            hint = R.string.settings_export_ics_s3_bucket_hint,
            value = prefsInteractor.getIcsExportS3Bucket(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
    }

    private fun onS3RegionClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_REGION_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_region,
            hint = R.string.settings_export_ics_s3_region_hint,
            value = prefsInteractor.getIcsExportS3Region(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
    }

    private fun onS3TimeoutClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_TIMEOUT_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_timeout,
            hint = R.string.settings_export_ics_s3_timeout_hint,
            value = prefsInteractor.getIcsExportS3TimeoutSeconds().toString(),
            inputType = InputType.TYPE_CLASS_NUMBER,
        )
    }

    private fun onS3AddressingClick() = delegateScope.launch {
        val selected = when (prefsInteractor.getIcsExportS3Addressing()) {
            S3Addressing.Path -> S3AddressingOption.Path
            S3Addressing.VirtualHost -> S3AddressingOption.VirtualHost
        }
        OptionsListParams(
            items = listOf(
                OptionsListParams.Item(
                    id = S3AddressingOption.Path,
                    text = resourceRepo.getString(R.string.settings_export_ics_s3_addressing_path),
                    icon = null,
                    isSelected = selected == S3AddressingOption.Path,
                ),
                OptionsListParams.Item(
                    id = S3AddressingOption.VirtualHost,
                    text = resourceRepo.getString(R.string.settings_export_ics_s3_addressing_virtual_host),
                    icon = null,
                    isSelected = selected == S3AddressingOption.VirtualHost,
                ),
            ),
        ).let(router::navigate)
    }

    private fun onS3TlsVerifyClick() = delegateScope.launch {
        val newValue = !prefsInteractor.getIcsExportS3TlsVerify()
        prefsInteractor.setIcsExportS3TlsVerify(newValue)
        parent?.updateContent()
    }

    private fun onS3ObjectKeyClick() = delegateScope.launch {
        openTextInputDialog(
            tag = ICS_EXPORT_S3_OBJECT_KEY_DIALOG_TAG,
            title = R.string.settings_export_ics_s3_object_key,
            hint = R.string.settings_export_ics_s3_object_key_hint,
            value = prefsInteractor.getIcsExportS3ObjectKeyTemplate(),
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
        )
    }

    private fun openTextInputDialog(
        tag: String,
        title: Int,
        hint: Int,
        value: String,
        inputType: Int,
    ) {
        TextInputDialogParams(
            tag = tag,
            title = resourceRepo.getString(title),
            hint = resourceRepo.getString(hint),
            value = value,
            inputType = inputType,
            btnPositive = resourceRepo.getString(R.string.ok),
            btnNegative = resourceRepo.getString(R.string.cancel),
        ).let(router::navigate)
    }

    private fun onCollapseClick() = delegateScope.launch {
        isCollapsed = isCollapsed.flip()
        parent?.updateContent()
    }

    companion object {
        private const val CSV_EXPORT_DIALOG_TAG = "csv_export_dialog_tag"
        private const val ICS_EXPORT_DIALOG_TAG = "ics_export_dialog_tag"
        private const val ICS_EXPORT_S3_ENDPOINT_DIALOG_TAG = "ics_export_s3_endpoint_dialog_tag"
        private const val ICS_EXPORT_S3_ACCESS_KEY_DIALOG_TAG = "ics_export_s3_access_key_dialog_tag"
        private const val ICS_EXPORT_S3_SECRET_KEY_DIALOG_TAG = "ics_export_s3_secret_key_dialog_tag"
        private const val ICS_EXPORT_S3_BUCKET_DIALOG_TAG = "ics_export_s3_bucket_dialog_tag"
        private const val ICS_EXPORT_S3_REGION_DIALOG_TAG = "ics_export_s3_region_dialog_tag"
        private const val ICS_EXPORT_S3_TIMEOUT_DIALOG_TAG = "ics_export_s3_timeout_dialog_tag"
        private const val ICS_EXPORT_S3_OBJECT_KEY_DIALOG_TAG = "ics_export_s3_object_key_dialog_tag"
        private const val BACKUP_RESTORE_DIALOG_TAG = "backup_restore_dialog_tag"
        private const val CSV_IMPORT_ALERT_DIALOG_TAG = "csv_import_alert_dialog_tag"
    }
}