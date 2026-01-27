package com.example.util.simpletimetracker.domain.backup.repo

import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.record.model.Range

interface IcsRepo {

    suspend fun saveIcsFile(
        uriString: String,
        range: Range?,
    ): ResultCode

    suspend fun uploadIcsFileToS3(
        config: S3Config,
        range: Range?,
    ): ResultCode
}