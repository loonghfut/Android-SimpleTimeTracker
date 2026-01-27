package com.example.util.simpletimetracker.core.repo

import androidx.lifecycle.LiveData

interface AutomaticIcsS3ExportRepo {

    val inProgress: LiveData<Boolean>
}
