package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "media_metadata", indices = [Index(value = ["full_path"], unique = true)])
data class MediaMetadata(
    @PrimaryKey(autoGenerate = true) var id: Int?,
    @ColumnInfo(name = "full_path") var fullPath: String,
    @ColumnInfo(name = "filename") var filename: String,
    @ColumnInfo(name = "parent_path") var parentPath: String,
    @ColumnInfo(name = "last_modified") var lastModified: Long,
    @ColumnInfo(name = "date_taken") var dateTaken: Long,
    @ColumnInfo(name = "date_taken_source") var dateTakenSource: Int,
    @ColumnInfo(name = "last_fixed") var lastFixed: Int,
    @ColumnInfo(name = "rating") var rating: Int,
    /** Newline-separated `dc:subject` tags. */
    @ColumnInfo(name = "tags") var tags: String,
) {
    fun tagList(): List<String> = decodeTags(tags)

    companion object {
        private const val TAG_SEPARATOR = "\n"

        fun encodeTags(tags: List<String>): String = tags.joinToString(TAG_SEPARATOR)

        fun decodeTags(encoded: String): List<String> =
            if (encoded.isEmpty()) emptyList() else encoded.split(TAG_SEPARATOR).filter { it.isNotEmpty() }
    }
}
