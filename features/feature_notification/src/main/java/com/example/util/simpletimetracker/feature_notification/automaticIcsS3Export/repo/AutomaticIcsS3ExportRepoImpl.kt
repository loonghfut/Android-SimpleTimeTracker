package com.example.util.simpletimetracker.feature_notification.automaticIcsS3Export.repo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.util.simpletimetracker.core.repo.AutomaticIcsS3ExportRepo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticIcsS3ExportRepoImpl @Inject constructor() : AutomaticIcsS3ExportRepo {

    override val inProgress: LiveData<Boolean> = MutableLiveData(false)
}
