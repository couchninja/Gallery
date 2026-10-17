package org.fossify.gallery.helpers

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isVideoFast
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

private val FILENAME_DATE_REGEX = Pattern.compile("""(?<!\d)(\d{4})(\d{2})(\d{2})[_\-](\d{2})(\d{2})(\d{2})(?!\d)""")
private val VIDEO_DATE_METADATA_KEY_CODES = intArrayOf(
    MediaMetadataRetriever.METADATA_KEY_DATE
)

object CaptureTimeReader {

    // Reject junk MediaStore values (small counters, unset fields) that land on 1 Jan 1970 after scaling.
    private const val MIN_PLAUSIBLE_CAPTURE_MILLIS = 31_536_000_000L // 1971-01-01 UTC

    fun isPlausibleCaptureTimeMillis(millis: Long): Boolean {
        if (millis < MIN_PLAUSIBLE_CAPTURE_MILLIS) {
            return false
        }
        return millis <= System.currentTimeMillis() + 86_400_000L
    }

    /**
     * MediaStore [Images.Media.DATE_TAKEN] is usually millis for photos, but many videos use epoch seconds.
     * Tiny non-zero values are treated as invalid.
     */
    fun normalizeCaptureTimeMillis(value: Long): Long {
        if (value <= 0L) {
            return 0L
        }

        val millis = when {
            value >= 1_000_000_000_000L -> value
            value >= 1_000_000_000L -> value * 1000L
            else -> 0L
        }

        return if (isPlausibleCaptureTimeMillis(millis)) millis else 0L
    }

    fun resolveCaptureTimeMillis(taken: Long, modifiedFallback: Long): Long {
        normalizeCaptureTimeMillis(taken).takeIf { it > 0L }?.let { return it }
        return if (isPlausibleCaptureTimeMillis(modifiedFallback)) {
            modifiedFallback
        } else {
            0L
        }
    }

    fun readCaptureTimeMillis(
        context: Context,
        path: String,
        filename: String,
        mediaStoreId: Long = 0L
    ): Long? {
        if (path.isVideoFast()) {
            readVideoCaptureTimeMillis(context, path, mediaStoreId)?.let { return normalizeCaptureTimeMillis(it) }
        }
        if (path.isImageFast()) {
            readExifCaptureTimeMillis(path)?.let { return normalizeCaptureTimeMillis(it) }
        }
        parseTimestampFromFilename(filename)?.let { return it }
        return null
    }

    fun readExifCaptureTimeMillis(path: String): Long? {
        return try {
            val exif = ExifInterface(path)
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            parseExifDateTime(dateTime)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun parseExifDateTime(dateTime: String): java.util.Date? {
        return try {
            val t = if (dateTime.length > 10 && dateTime.substring(10, 11) == "T") "\'T\'" else " "
            val separator = dateTime.substring(4, 5)
            val format = "yyyy${separator}MM${separator}dd${t}kk:mm:ss"
            SimpleDateFormat(format, Locale.getDefault()).parse(dateTime)
        } catch (_: Exception) {
            null
        }
    }

    fun parseTimestampFromFilename(filename: String): Long? {
        val matcher = FILENAME_DATE_REGEX.matcher(filename)
        if (!matcher.find()) {
            return null
        }

        return try {
            val year = matcher.group(1)!!.toInt()
            val month = matcher.group(2)!!.toInt()
            val day = matcher.group(3)!!.toInt()
            val hour = matcher.group(4)!!.toInt()
            val minute = matcher.group(5)!!.toInt()
            val second = matcher.group(6)!!.toInt()
            if (year < 1970 || year > 2100 || month !in 1..12 || day !in 1..31) {
                return null
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(year, month - 1, day, hour, minute, second)
            calendar.set(Calendar.MILLISECOND, 0)
            val millis = calendar.timeInMillis
            if (millis > System.currentTimeMillis()) {
                null
            } else {
                millis
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readVideoCaptureTimeMillis(context: Context, path: String, mediaStoreId: Long): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!setRetrieverDataSource(retriever, context, path, mediaStoreId)) {
                return null
            }
            for (keyCode in VIDEO_DATE_METADATA_KEY_CODES) {
                val parsed = retriever.extractMetadata(keyCode)?.let(::parseVideoMetadataDate)
                if (parsed != null) {
                    return parsed
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun setRetrieverDataSource(
        retriever: MediaMetadataRetriever,
        context: Context,
        path: String,
        mediaStoreId: Long
    ): Boolean {
        if (mediaStoreId > 0L) {
            try {
                val collection = if (path.isVideoFast()) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val uri = ContentUris.withAppendedId(collection, mediaStoreId)
                retriever.setDataSource(context, uri)
                return true
            } catch (_: Exception) {
            }
        }

        return try {
            retriever.setDataSource(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseVideoMetadataDate(raw: String): Long? {
        val trimmed = raw.trim()
        parseInstant(trimmed)?.toEpochMilli()?.let { return it }

        val formats = arrayOf(
            "yyyyMMdd'T'HHmmss.SSS'Z'",
            "yyyyMMdd'T'HHmmss'Z'",
            "yyyyMMdd'T'HHmmss.SSS",
            "yyyyMMdd'T'HHmmss",
            "yyyyMMdd HHmmss",
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        for (pattern in formats) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) {
                    formatter.timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.parse(trimmed)?.time?.let { return it }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun parseInstant(value: String): Instant? {
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()?.let { return it }
        return null
    }

    fun fileLastModifiedMillis(path: String): Long = runCatching { File(path).lastModified() }.getOrDefault(0L)
}
