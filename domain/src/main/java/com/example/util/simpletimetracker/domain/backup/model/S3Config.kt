package com.example.util.simpletimetracker.domain.backup.model

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String,
    val timeoutSeconds: Int,
    val addressing: S3Addressing,
    val tlsVerify: Boolean,
    val objectKeyTemplate: String,
)
