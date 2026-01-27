package com.example.util.simpletimetracker.domain.backup.interactor

import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.backup.repo.IcsRepo
import com.example.util.simpletimetracker.domain.record.model.Range
import javax.inject.Inject

class IcsExportInteractor @Inject constructor(
    private val icsRepo: IcsRepo,
) {

    suspend fun saveIcsFile(uriString: String, range: Range?): ResultCode {
        return icsRepo.saveIcsFile(uriString = uriString, range = range)
    }

    suspend fun uploadIcsFileToS3(config: S3Config, range: Range?): ResultCode {
        return icsRepo.uploadIcsFileToS3(config = config, range = range)
    }
}