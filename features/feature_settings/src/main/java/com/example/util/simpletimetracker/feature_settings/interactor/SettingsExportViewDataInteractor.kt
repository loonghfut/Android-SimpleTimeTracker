package com.example.util.simpletimetracker.feature_settings.interactor

import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.feature_base_adapter.ViewHolderType
import com.example.util.simpletimetracker.feature_settings.R
import com.example.util.simpletimetracker.feature_settings.api.SettingsBlock
import com.example.util.simpletimetracker.feature_settings.mapper.SettingsMapper
import com.example.util.simpletimetracker.feature_settings.viewData.ExportDateTimeFormatViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsBottomViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsCheckboxViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsCollapseViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsHintViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsSelectorViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsSpinnerViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsTextColor
import com.example.util.simpletimetracker.feature_settings.views.SettingsTextViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsTextWithButtonViewData
import com.example.util.simpletimetracker.feature_settings.views.SettingsTopViewData
import javax.inject.Inject

class SettingsExportViewDataInteractor @Inject constructor(
    private val resourceRepo: ResourceRepo,
    private val prefsInteractor: PrefsInteractor,
    private val settingsMapper: SettingsMapper,
    private val settingsCommonInteractor: SettingsCommonInteractor,
) {

    suspend fun execute(
        isCollapsed: Boolean,
    ): List<ViewHolderType> {
        val isDarkTheme = prefsInteractor.getDarkMode()
        val result = mutableListOf<ViewHolderType>()

        result += SettingsTopViewData(
            block = SettingsBlock.ExportTop,
        )

        result += SettingsCollapseViewData(
            block = SettingsBlock.ExportCollapse,
            title = resourceRepo.getString(R.string.settings_export_title),
            opened = !isCollapsed,
            iconResId = R.drawable.import_export,
            iconColor = (if (isDarkTheme) R.color.green_300 else R.color.green_200)
                .let(resourceRepo::getColor),
            dividerIsVisible = !isCollapsed,
        )

        if (!isCollapsed) {
            result += SettingsTextViewData(
                block = SettingsBlock.ExportSpreadsheet,
                title = resourceRepo.getString(R.string.settings_export_csv),
                subtitle = resourceRepo.getString(R.string.settings_export_csv_description),
                hint = resourceRepo.getString(R.string.settings_export_warning),
                hintColor = SettingsTextColor.Attention,
            )

            val automaticExportEnabled = loadAutomaticExportEnabled()
            val automaticExportLastSaveTime = loadAutomaticExportLastSaveTime()
            val automaticExportLastSaveTimeVisible = automaticExportLastSaveTime.isNotEmpty()
            result += SettingsCheckboxViewData(
                block = SettingsBlock.ExportSpreadsheetAutomatic,
                title = resourceRepo.getString(R.string.settings_automatic_export),
                subtitle = resourceRepo.getString(R.string.settings_automatic_description),
                isChecked = automaticExportEnabled,
                bottomSpaceIsVisible = !automaticExportEnabled,
                dividerIsVisible = !automaticExportEnabled,
                forceBind = true,
            )
            if (automaticExportLastSaveTimeVisible) {
                result += SettingsHintViewData(
                    block = SettingsBlock.ExportSpreadsheetAutomaticHint,
                    text = automaticExportLastSaveTime,
                    textColor = SettingsTextColor.Success,
                    topSpaceIsVisible = false,
                    dividerIsVisible = false,
                    bottomSpaceIsVisible = false,
                )
            }
            if (automaticExportEnabled) {
                result += SettingsSelectorViewData(
                    block = SettingsBlock.ExportSpreadsheetAutomaticTime,
                    title = resourceRepo.getString(R.string.settings_automatic_save_time),
                    subtitle = "",
                    selectedValue = loadAutomaticExportTriggerTime(),
                    bottomSpaceIsVisible = true,
                    dividerIsVisible = true,
                )
            }

            result += SettingsTextViewData(
                block = SettingsBlock.ExportCustomized,
                title = resourceRepo.getString(R.string.settings_backup_options),
                subtitle = "",
                dividerIsVisible = false,
            )
        }

        result += SettingsBottomViewData(
            block = SettingsBlock.ExportBottom,
        )

        return result
    }

    suspend fun executeAdvanced(): List<ViewHolderType> {
        val result = mutableListOf<ViewHolderType>()

        result += SettingsTextWithButtonViewData(
            buttonBlock = SettingsBlock.ExportSpreadsheetImportHint,
            data = SettingsTextViewData(
                block = SettingsBlock.ExportSpreadsheetImport,
                title = resourceRepo.getString(R.string.settings_import_csv),
                subtitle = resourceRepo.getString(R.string.settings_import_csv_description),
                hint = resourceRepo.getString(R.string.data_edit_hint),
                hintColor = SettingsTextColor.Attention,
            ),
        )

        val dateTimeFormatViewData = loadDateTimeFormatViewData()
        result += SettingsSpinnerViewData(
            block = SettingsBlock.ExportSpreadsheetDateTimeFormat,
            title = resourceRepo.getString(R.string.settings_export_csv_format),
            value = dateTimeFormatViewData.items
                .getOrNull(dateTimeFormatViewData.selectedPosition)?.text.orEmpty(),
            items = dateTimeFormatViewData.items,
            selectedPosition = dateTimeFormatViewData.selectedPosition,
            processSameItemSelected = false,
            dividerIsVisible = false,
            bottomSpaceIsVisible = false,
        )
        result += SettingsHintViewData(
            block = SettingsBlock.ExportSpreadsheetDateTimeFormatHint,
            text = loadDateTimeFormatHintViewData(),
            topSpaceIsVisible = false,
        )

        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcs,
            title = resourceRepo.getString(R.string.settings_export_ics),
            subtitle = resourceRepo.getString(R.string.settings_export_warning),
            subtitleColor = SettingsTextColor.Attention,
        )

        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Endpoint,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_endpoint),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_endpoint_description),
            hint = loadValueHint(prefsInteractor.getIcsExportS3Endpoint()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3AccessKey,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_access_key),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_access_key_description),
            hint = maskValue(prefsInteractor.getIcsExportS3AccessKey()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3SecretKey,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_secret_key),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_secret_key_description),
            hint = maskSecret(prefsInteractor.getIcsExportS3SecretKey()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Bucket,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_bucket),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_bucket_description),
            hint = loadValueHint(prefsInteractor.getIcsExportS3Bucket()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Region,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_region),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_region_description),
            hint = loadValueHint(prefsInteractor.getIcsExportS3Region()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Timeout,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_timeout),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_timeout_description),
            hint = loadTimeoutHint(prefsInteractor.getIcsExportS3TimeoutSeconds()),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Addressing,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_addressing),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_addressing_description),
            hint = loadAddressingHint(),
        )
        result += SettingsCheckboxViewData(
            block = SettingsBlock.ExportIcsS3TlsVerify,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_tls_verify),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_tls_verify_description),
            isChecked = prefsInteractor.getIcsExportS3TlsVerify(),
            bottomSpaceIsVisible = true,
            dividerIsVisible = true,
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3ObjectKey,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_object_key),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_object_key_description),
            hint = loadObjectKeyHint(),
        )
        result += SettingsTextViewData(
            block = SettingsBlock.ExportIcsS3Upload,
            title = resourceRepo.getString(R.string.settings_export_ics_s3_upload),
            subtitle = resourceRepo.getString(R.string.settings_export_ics_s3_upload_description),
        )

        val automaticUploadEnabled = loadAutomaticIcsS3UploadEnabled()
        val automaticUploadLastSaveTime = loadAutomaticIcsS3UploadLastSaveTime()
        val automaticUploadLastSaveTimeVisible = automaticUploadLastSaveTime.isNotEmpty()
        result += SettingsCheckboxViewData(
            block = SettingsBlock.ExportIcsS3Automatic,
            title = resourceRepo.getString(R.string.settings_automatic_ics_s3_upload),
            subtitle = resourceRepo.getString(R.string.settings_automatic_ics_s3_upload_description),
            isChecked = automaticUploadEnabled,
            bottomSpaceIsVisible = !automaticUploadEnabled,
            dividerIsVisible = !automaticUploadEnabled,
            forceBind = true,
        )
        if (automaticUploadLastSaveTimeVisible) {
            result += SettingsHintViewData(
                block = SettingsBlock.ExportIcsS3AutomaticHint,
                text = automaticUploadLastSaveTime,
                textColor = SettingsTextColor.Success,
                topSpaceIsVisible = false,
                dividerIsVisible = false,
                bottomSpaceIsVisible = false,
            )
        }
        if (automaticUploadEnabled) {
            result += SettingsSelectorViewData(
                block = SettingsBlock.ExportIcsS3AutomaticTime,
                title = resourceRepo.getString(R.string.settings_automatic_save_time),
                subtitle = "",
                selectedValue = loadAutomaticIcsS3UploadTriggerTime(),
                bottomSpaceIsVisible = true,
                dividerIsVisible = true,
            )
        }

        if (loadAutomaticExportEnabled()) {
            result += SettingsTextViewData(
                block = SettingsBlock.ExportTriggerAutoBackup,
                title = resourceRepo.getString(R.string.backup_options_trigger_auto_export),
                subtitle = "",
            )
        }

        return result
    }

    private suspend fun loadAutomaticExportEnabled(): Boolean {
        return prefsInteractor.getAutomaticExportUri().isNotEmpty()
    }

    private suspend fun loadAutomaticExportLastSaveTime(): String {
        return if (loadAutomaticExportEnabled()) {
            settingsCommonInteractor.getLastSaveString(
                prefsInteractor.getAutomaticExportLastSaveTime(),
            )
        } else {
            ""
        }
    }

    private suspend fun loadAutomaticExportTriggerTime(): String {
        return settingsMapper.toStartOfDayText(
            startOfDayShift = prefsInteractor.getAutomaticExportTriggerTime(),
            useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
        )
    }

    private suspend fun loadAutomaticIcsS3UploadEnabled(): Boolean {
        return prefsInteractor.getIcsExportS3AutomaticEnabled()
    }

    private suspend fun loadAutomaticIcsS3UploadLastSaveTime(): String {
        return if (loadAutomaticIcsS3UploadEnabled()) {
            settingsCommonInteractor.getLastSaveString(
                prefsInteractor.getIcsExportS3AutomaticLastSaveTime(),
            )
        } else {
            ""
        }
    }

    private suspend fun loadAutomaticIcsS3UploadTriggerTime(): String {
        return settingsMapper.toStartOfDayText(
            startOfDayShift = prefsInteractor.getIcsExportS3AutomaticTriggerTime(),
            useMilitaryTime = prefsInteractor.getUseMilitaryTimeFormat(),
        )
    }

    private suspend fun loadDateTimeFormatViewData(): ExportDateTimeFormatViewData {
        return prefsInteractor.getCsvExportDateTimeFormat()
            .let(settingsMapper::toCsvExportDateTimeFormat)
    }

    private suspend fun loadDateTimeFormatHintViewData(): String {
        return prefsInteractor.getCsvExportDateTimeFormat()
            .let(settingsMapper::toCsvExportDateTimeFormatHint)
    }

    private suspend fun loadAddressingHint(): String {
        return when (prefsInteractor.getIcsExportS3Addressing()) {
            com.example.util.simpletimetracker.domain.backup.model.S3Addressing.Path ->
                resourceRepo.getString(R.string.settings_export_ics_s3_addressing_path)
            com.example.util.simpletimetracker.domain.backup.model.S3Addressing.VirtualHost ->
                resourceRepo.getString(R.string.settings_export_ics_s3_addressing_virtual_host)
        }
    }

    private fun loadValueHint(value: String): String {
        return if (value.isBlank()) {
            resourceRepo.getString(R.string.settings_export_ics_s3_value_empty)
        } else {
            value
        }
    }

    private fun loadTimeoutHint(value: Int): String {
        return if (value <= 0) {
            resourceRepo.getString(R.string.settings_export_ics_s3_value_empty)
        } else {
            resourceRepo.getString(R.string.settings_export_ics_s3_timeout_value, value)
        }
    }

    private suspend fun loadObjectKeyHint(): String {
        val value = prefsInteractor.getIcsExportS3ObjectKeyTemplate().trim()
        return if (value.isBlank()) {
            resourceRepo.getString(R.string.settings_export_ics_s3_object_key_default)
        } else {
            value
        }
    }

    private fun maskValue(value: String): String {
        return if (value.isBlank()) {
            resourceRepo.getString(R.string.settings_export_ics_s3_value_empty)
        } else {
            val suffix = value.takeLast(4)
            "••••$suffix"
        }
    }

    private fun maskSecret(value: String): String {
        return if (value.isBlank()) {
            resourceRepo.getString(R.string.settings_export_ics_s3_value_empty)
        } else {
            "••••••••"
        }
    }
}