package com.example.util.simpletimetracker.data_local.file

import android.content.ContentResolver
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.example.util.simpletimetracker.core.mapper.RecordTagFullNameMapper
import com.example.util.simpletimetracker.core.repo.ResourceRepo
import com.example.util.simpletimetracker.data_local.R
import com.example.util.simpletimetracker.domain.backup.model.S3Addressing
import com.example.util.simpletimetracker.domain.backup.model.ResultCode
import com.example.util.simpletimetracker.domain.backup.model.S3Config
import com.example.util.simpletimetracker.domain.backup.repo.IcsRepo
import com.example.util.simpletimetracker.domain.category.model.Category
import com.example.util.simpletimetracker.domain.category.repo.CategoryRepo
import com.example.util.simpletimetracker.domain.category.repo.RecordTypeCategoryRepo
import com.example.util.simpletimetracker.domain.record.model.Range
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.record.repo.RecordRepo
import com.example.util.simpletimetracker.domain.recordTag.model.RecordTag
import com.example.util.simpletimetracker.domain.recordTag.repo.RecordTagRepo
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.recordType.repo.RecordTypeRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.inject.Inject

class IcsRepoImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val recordTypeRepo: RecordTypeRepo,
    private val categoryRepo: CategoryRepo,
    private val recordRepo: RecordRepo,
    private val recordTypeCategoryRepo: RecordTypeCategoryRepo,
    private val recordTagRepo: RecordTagRepo,
    private val resourceRepo: ResourceRepo,
    private val recordTagFullNameMapper: RecordTagFullNameMapper,
) : IcsRepo {

    private val commentTitle: String by lazy {
        resourceRepo.getString(R.string.change_record_comment_field)
    }
    private val categoryTitle: String by lazy {
        resourceRepo.getString(R.string.category_hint)
    }
    private val tagsTitle: String by lazy {
        resourceRepo.getString(R.string.record_tag_hint)
    }

    override suspend fun saveIcsFile(
        uriString: String,
        range: Range?,
    ): ResultCode = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var fileOutputStream: BufferedOutputStream? = null

        try {
            val uri = uriString.toUri()
            fileDescriptor = contentResolver.openFileDescriptor(uri, "w")
            fileOutputStream = fileDescriptor?.fileDescriptor
                ?.let(::FileOutputStream)?.buffered()

            fileOutputStream?.let { writeIcs(it, range) }

            fileOutputStream?.close()
            fileDescriptor?.close()
            ResultCode.Success(resourceRepo.getString(R.string.message_export_complete))
        } catch (e: Exception) {
            Timber.e(e)
            ResultCode.Error(resourceRepo.getString(R.string.message_export_error))
        } finally {
            try {
                fileOutputStream?.close()
                fileDescriptor?.close()
            } catch (e: IOException) {
                // Do nothing
            }
        }
    }

    override suspend fun uploadIcsFileToS3(
        config: S3Config,
        range: Range?,
    ): ResultCode = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val objectKey = buildObjectKey(config.objectKeyTemplate)
            val url = buildS3Url(config, objectKey)
            val connectionResult = buildConnection(url, config)
            connection = connectionResult.connection

            val amzDate = connectionResult.amzDate
            val authorization = buildAuthorizationHeader(
                url = url,
                amzDate = amzDate,
                region = config.region,
                accessKey = config.accessKey,
                secretKey = config.secretKey,
                contentSha256 = UNSIGNED_PAYLOAD,
            )

            connection.setRequestProperty("x-amz-date", amzDate)
            connection.setRequestProperty("x-amz-content-sha256", UNSIGNED_PAYLOAD)
            connection.setRequestProperty("Authorization", authorization)

            val outputStream = connection.outputStream.buffered()
            try {
                writeIcs(outputStream, range)
            } finally {
                outputStream.close()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                ResultCode.Success(resourceRepo.getString(R.string.message_export_complete))
            } else {
                Timber.e("S3 upload failed: HTTP $responseCode")
                ResultCode.Error(resourceRepo.getString(R.string.message_export_error))
            }
        } catch (e: Exception) {
            Timber.e(e)
            ResultCode.Error(resourceRepo.getString(R.string.message_export_error))
        } finally {
            connection?.disconnect()
        }
    }

    private data class ConnectionResult(
        val connection: HttpURLConnection,
        val amzDate: String,
    )

    private fun buildConnection(url: URL, config: S3Config): ConnectionResult {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            useCaches = false
            connectTimeout = config.timeoutSeconds * 1000
            readTimeout = config.timeoutSeconds * 1000
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "text/calendar")
        }

        if (!config.tlsVerify && connection is HttpsURLConnection) {
            connection.sslSocketFactory = trustAllSslSocketFactory()
            connection.hostnameVerifier = trustAllHostnameVerifier()
        }

        val amzDate = amzDateFormat.format(Date())
        return ConnectionResult(connection = connection, amzDate = amzDate)
    }

    private fun buildS3Url(config: S3Config, objectKey: String): URL {
        val endpoint = normalizeEndpoint(config.endpoint)
        val endpointUri = URI(endpoint)
        val basePath = endpointUri.path.orEmpty().trimEnd('/')
        val authority = endpointUri.authority

        val (host, path) = when (config.addressing) {
            S3Addressing.VirtualHost -> {
                val host = "${config.bucket}.$authority"
                host to "$basePath/${objectKey}"
            }
            S3Addressing.Path -> {
                val host = authority
                host to "$basePath/${config.bucket}/${objectKey}"
            }
        }

        val scheme = endpointUri.scheme ?: "https"
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return URL("$scheme://$host$normalizedPath")
    }

    private fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun buildObjectKey(customTemplate: String?): String {
        val template = customTemplate
            ?.replace("\\", "/")
            ?.trim()
            .takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_OBJECT_KEY_TEMPLATE
        val timestamp = fileNameDateFormat.format(Date())
        val withDate = template.replace("{$FILE_EXPORT_DATE_TAG}", timestamp)
        val normalized = withDate.removePrefix("/")
        return if (normalized.endsWith(".ics", ignoreCase = true)) {
            normalized
        } else {
            "$normalized.ics"
        }
    }

    private fun buildAuthorizationHeader(
        url: URL,
        amzDate: String,
        region: String,
        accessKey: String,
        secretKey: String,
        contentSha256: String,
    ): String {
        val dateStamp = amzDate.substring(0, 8)
        val canonicalUri = encodePath(url.path)
        val canonicalQueryString = ""
        val hostHeader = buildHostHeader(url)
        val canonicalHeaders = buildString {
            append("host:$hostHeader\n")
            append("x-amz-content-sha256:$contentSha256\n")
            append("x-amz-date:$amzDate\n")
        }
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"

        val canonicalRequest = listOf(
            "PUT",
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaders,
            contentSha256,
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest),
        ).joinToString("\n")

        val signingKey = getSignatureKey(secretKey, dateStamp, region, "s3")
        val signature = hmacSha256Hex(signingKey, stringToSign)

        return "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
    }

    private fun buildHostHeader(url: URL): String {
        val port = url.port
        val defaultPort = if (url.protocol == "https") 443 else 80
        return if (port == -1 || port == defaultPort) {
            url.host
        } else {
            "${url.host}:$port"
        }
    }

    private fun encodePath(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return normalized.split("/")
            .joinToString("/") { segment ->
                if (segment.isEmpty()) "" else encodeSegment(segment)
            }
            .replace("//", "/")
    }

    private fun encodeSegment(segment: String): String {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
    }

    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val raw = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return raw.joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun getSignatureKey(
        key: String,
        dateStamp: String,
        regionName: String,
        serviceName: String,
    ): ByteArray {
        val kDate = hmacSha256(("AWS4" + key).toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        return hmacSha256(kService, "aws4_request")
    }

    private fun trustAllSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            },
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }

    private fun trustAllHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { _, _ -> true }
    }

    private suspend fun writeIcs(
        outputStream: OutputStream,
        range: Range?,
    ) {
        outputStream.write(ICS_HEADER.toByteArray())

        val recordTypes = recordTypeRepo.getAll().associateBy { it.id }
        val categories = categoryRepo.getAll().associateBy { it.id }
        val recordTags = recordTagRepo.getAll()
        val typeToCategories = recordTypes.map { (id, _) ->
            id to recordTypeCategoryRepo.getCategoryIdsByType(id).mapNotNull { categories[it] }
        }.toMap()

        val records = if (range != null) {
            recordRepo.getFromRange(range)
        } else {
            recordRepo.getAll()
        }
        records
            .sortedBy { it.timeStarted }
            .forEach { record ->
                val tagIds = record.tags.map(RecordBase.Tag::tagId)
                toIcsString(
                    record = record,
                    recordType = recordTypes[record.typeId],
                    categories = typeToCategories[record.typeId].orEmpty(),
                    recordTags = recordTags.filter { it.id in tagIds },
                    recordTagsData = record.tags,
                )
                    ?.toByteArray()
                    ?.let { outputStream.write(it) }
            }

        outputStream.write(ICS_FOOTER.toByteArray())
        outputStream.flush()
    }

    private fun toIcsString(
        record: Record,
        recordType: RecordType?,
        categories: List<Category>,
        recordTags: List<RecordTag>,
        recordTagsData: List<RecordBase.Tag>,
    ): String? {
        if (recordType == null) return null

        val commentString = record.comment.clean()
            .wrapText(commentTitle)
        val categoriesString = categories
            .joinToString(separator = ", ", transform = { it.name.clean() })
            .wrapText(categoryTitle)
        val tagsString = recordTagFullNameMapper
            .getFullName(tags = recordTags, tagData = recordTagsData)
            .clean()
            .wrapText(tagsTitle)
        val description = commentString + categoriesString + tagsString
        val categoriesProperty = categories
            .joinToString(separator = ",", transform = { it.name.clean() })

        return buildString {
            append("BEGIN:VEVENT\n")
            append("DTSTART:${formatDateTime(record.timeStarted)}\n")
            append("DTEND:${formatDateTime(record.timeEnded)}\n")
            append("UID:recordId_${record.id}@stt\n")
            append("SUMMARY:${recordType.name.clean()}\n")
            if (description.isNotEmpty()) {
                append("DESCRIPTION:$description\n")
            }
            if (categoriesProperty.isNotEmpty()) {
                append("CATEGORIES:$categoriesProperty\n")
            }
            append("END:VEVENT\n")
        }
    }

    private fun formatDateTime(timestamp: Long): String {
        synchronized(dateTimeFormat) {
            return dateTimeFormat.format(timestamp)
        }
    }

    private fun String.wrapText(
        title: String,
    ): String = this
        .takeUnless { it.isEmpty() }
        ?.let { "$title: $it\\n" }
        .orEmpty()

    private fun String.clean() =
        replace("\n", "\\n")

    companion object {
        private const val ICS_HEADER = "BEGIN:VCALENDAR\n" +
            "PRODID:-//Simple Time Tracker//EN\n" +
            "VERSION:2.0\n"
        private const val ICS_FOOTER = "END:VCALENDAR"
        private const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
        private const val FILE_EXPORT_DATE_TAG = "date"
        private const val DEFAULT_OBJECT_KEY_TEMPLATE = "stt_events_{$FILE_EXPORT_DATE_TAG}.ics"

        private val dateTimeFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        private val amzDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
}