package com.example.util.simpletimetracker.feature_settings.model

import com.example.util.simpletimetracker.navigation.params.screen.OptionsListParams
import kotlinx.parcelize.Parcelize

@Parcelize
enum class S3AddressingOption : OptionsListParams.Item.Id {
    Path,
    VirtualHost,
}
