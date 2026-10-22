package org.fossify.gallery.helpers

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.provider.MediaStore.Images
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import org.fossify.commons.extensions.isImageFast
import org.fossify.commons.extensions.isVideoFast
import org.fossify.gallery.extensions.config
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.HashMap
import java.util.regex.Pattern

private val FILENAME_DATE_REGEX = Pattern.compile("""(?<!\d)(\d{4})(\d{2})(\d{2})[_\-](\d{2})(\d{2})(\d{2})(?!\d)""")
private val FILENAME_COMPACT_DATE_REGEX = Pattern.compile("""(?i)\bIMG[-_]?(\d{4})(\d{2})(\d{2})\b""")
private val VIDEO_DATE_METADATA_KEY_CODES = intArrayOf(
    MediaMetadataRetriever.METADATA_KEY_DATE
)

object CaptureTimeReader {

    // Reject junk MediaStore values (small counters, unset fields) that land on 1 Jan 1970 after scaling.
    private const val MIN_PLAUSIBLE_CAPTURE_MILLIS = 31_536_000_000L // 1971-01-01 UTC

    // Pre-1970 captures (e.g. scanned historic photos) are negative millis; 0 stays the "missing" sentinel,
    // so values within a day before the epoch are treated as junk like -1.
    private const val MIN_HISTORIC_CAPTURE_MILLIS = -5_364_662_400_000L // 1800-01-01 UTC
    private const val MAX_PRE_EPOCH_CAPTURE_MILLIS = -86_400_000L

    // MP4/QuickTime creation_time 0 decodes to 1904-01-01 UTC; videos without a real date carry it.
    private const val QUICKTIME_EPOCH_MILLIS = -2_082_844_800_000L
    private const val DAY_MILLIS = 86_400_000L

    fun isPlausibleCaptureTimeMillis(millis: Long): Boolean {
        if (millis < 0L) {
            if (millis in (QUICKTIME_EPOCH_MILLIS - DAY_MILLIS)..(QUICKTIME_EPOCH_MILLIS + DAY_MILLIS)) {
                return false
            }
            return millis in MIN_HISTORIC_CAPTURE_MILLIS..MAX_PRE_EPOCH_CAPTURE_MILLIS
        }
        if (millis < MIN_PLAUSIBLE_CAPTURE_MILLIS) {
            return false
        }
        return millis <= System.currentTimeMillis() + 86_400_000L
    }

    /**
     * MediaStore [Images.Media.DATE_TAKEN] is usually millis for photos, but many videos use epoch seconds.
     * Values between 1e9 and 1e12 can be either seconds or millis (e.g. 1999-01-01 is ~9.15e11 ms).
     * Negative values are pre-1970 millis. Returns 0 when the value is not a usable capture time.
     */
    fun normalizeCaptureTimeMillis(value: Long): Long {
        if (value == 0L) {
            return 0L
        }

        if (value < 0L) {
            return if (isPlausibleCaptureTimeMillis(value)) value else 0L
        }

        if (value >= 1_000_000_000_000L) {
            return if (isPlausibleCaptureTimeMillis(value)) value else 0L
        }

        if (value >= 100_000_000_000L) {
            if (isPlausibleCaptureTimeMillis(value)) {
                return value
            }
        }

        if (value >= 1_000_000_000L) {
            val asMillisFromSeconds = value * 1000L
            if (isPlausibleCaptureTimeMillis(asMillisFromSeconds)) {
                return asMillisFromSeconds
            }
        }

        return 0L
    }

    fun resolveCaptureTimeMillis(taken: Long, modifiedFallback: Long): Long {
        normalizeCaptureTimeMillis(taken).takeIf { it != 0L }?.let { return it }
        return if (isPlausibleCaptureTimeMillis(modifiedFallback)) {
            modifiedFallback
        } else {
            0L
        }
    }

    fun lookupAndRemoveDateTaken(dateTakens: HashMap<String, Long>, path: String): Long? {
        dateTakens.remove(path)?.let { return it }
        val matchingKey = dateTakens.keys.firstOrNull { it.equals(path, ignoreCase = true) } ?: return null
        return dateTakens.remove(matchingKey)
    }

    fun resolveDateTakenForSort(
        context: Context,
        path: String,
        filename: String,
        mediaStoreId: Long,
        dateTakens: HashMap<String, Long>,
        mediaStoreDateTaken: Long,
        lastModified: Long
    ): Long {
        lookupAndRemoveDateTaken(dateTakens, path)?.let { cached ->
            normalizeCaptureTimeMillis(cached).takeIf { it != 0L }?.let { return it }
        }
        normalizeCaptureTimeMillis(mediaStoreDateTaken).takeIf { it != 0L }?.let { return it }
        readCaptureTimeMillis(context, path, filename, mediaStoreId)?.let { return it }
        return resolveCaptureTimeMillis(0L, lastModified)
    }

    fun readCaptureTimeMillis(
        context: Context,
        path: String,
        filename: String,
        mediaStoreId: Long = 0L
    ): Long? {
        val fromMetadata = when {
            path.isVideoFast() -> readVideoCaptureTimeMillis(context, path, mediaStoreId)
            path.isImageFast() -> readExifCaptureTimeMillis(context, path, mediaStoreId)
            else -> null
        }
        val raw = fromMetadata ?: if (context.config.guessCaptureDateFromFilename) {
            parseTimestampFromFilename(filename) ?: parseCompactDateFromFilename(filename)
        } else {
            null
        }
        return raw?.let { millis -> normalizeCaptureTimeMillis(millis).takeIf { it != 0L } }
    }

    fun readExifCaptureTimeMillis(path: String): Long? {
        return readExifCaptureTimeMillisFromExif(runCatching { ExifInterface(path) }.getOrNull())
    }

    fun readExifCaptureTimeMillis(context: Context, path: String, mediaStoreId: Long = 0L): Long? {
        for (exif in openExifInterfaces(context, path, mediaStoreId)) {
            readExifCaptureTimeMillisFromExif(exif)?.let { return it }
        }
        return null
    }

    private fun readExifCaptureTimeMillisFromExif(exif: ExifInterface?): Long? {
        if (exif == null) {
            return null
        }
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return parseExifDateTime(dateTime)?.time
    }

    private fun openExifInterfaces(context: Context, path: String, mediaStoreId: Long): List<ExifInterface> {
        val opened = ArrayList<ExifInterface>()
        fun add(block: () -> ExifInterface?) {
            runCatching { block() }.getOrNull()?.let { opened.add(it) }
        }

        if (path.isNotEmpty()) {
            add { ExifInterface(path) }
            add {
                FileInputStream(path).use { stream -> ExifInterface(stream) }
            }
        }

        if (mediaStoreId > 0L) {
            val uri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
            add {
                context.contentResolver.openInputStream(uri)?.use { stream -> ExifInterface(stream) }
            }
            add {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    ExifInterface(pfd.fileDescriptor)
                }
            }
        }

        if (path.startsWith("content://")) {
            val uri = path.toUri()
            add {
                context.contentResolver.openInputStream(uri)?.use { stream -> ExifInterface(stream) }
            }
        }

        return opened
    }

    fun parseExifDateTime(dateTime: String): java.util.Date? {
        return try {
            val t = if (dateTime.length > 10 && dateTime.substring(10, 11) == "T") "\'T\'" else " "
            val separator = dateTime.substring(4, 5)
            val format = "yyyy${separator}MM${separator}dd${t}HH:mm:ss"
            SimpleDateFormat(format, Locale.US).parse(dateTime)
        } catch (_: Exception) {
            null
        }
    }

    fun parseTimestampFromFilename(filename: String): Long? {
        val matcher = FILENAME_DATE_REGEX.matcher(filename)
        if (!matcher.find()) {
            return null
        }

        return calendarMillisUtc(
            year = matcher.group(1)!!.toInt(),
            month = matcher.group(2)!!.toInt(),
            day = matcher.group(3)!!.toInt(),
            hour = matcher.group(4)!!.toInt(),
            minute = matcher.group(5)!!.toInt(),
            second = matcher.group(6)!!.toInt()
        )
    }

    fun parseCompactDateFromFilename(filename: String): Long? {
        val matcher = FILENAME_COMPACT_DATE_REGEX.matcher(filename)
        if (!matcher.find()) {
            return null
        }
        return calendarMillisUtc(
            year = matcher.group(1)!!.toInt(),
            month = matcher.group(2)!!.toInt(),
            day = matcher.group(3)!!.toInt(),
            hour = 0,
            minute = 0,
            second = 0
        )
    }

    private fun calendarMillisUtc(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): Long? {
        return try {
            if (year < 1970 || year > 2100 || month !in 1..12 || day !in 1..31) {
                return null
            }

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
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
