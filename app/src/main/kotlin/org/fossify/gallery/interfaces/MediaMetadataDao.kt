package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.gallery.helpers.DATE_TAKEN_SOURCE_MANUAL
import org.fossify.gallery.helpers.DATE_TAKEN_SOURCE_PENDING
import org.fossify.gallery.models.MediaMetadata

@Dao
interface MediaMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<MediaMetadata>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(entry: MediaMetadata): Long

    @Query("SELECT full_path, filename, parent_path, last_modified, date_taken, date_taken_source, last_fixed, rating, tags FROM media_metadata WHERE parent_path = :path COLLATE NOCASE")
    fun getFromParent(path: String): List<MediaMetadata>

    @Query("SELECT full_path, filename, parent_path, last_modified, date_taken, date_taken_source, last_fixed, rating, tags FROM media_metadata")
    fun getAll(): List<MediaMetadata>

    @Query("SELECT full_path, filename, parent_path, last_modified, date_taken, date_taken_source, last_fixed, rating, tags FROM media_metadata WHERE full_path = :path COLLATE NOCASE LIMIT 1")
    fun getByPath(path: String): MediaMetadata?

    @Query("DELETE FROM media_metadata WHERE full_path = :path COLLATE NOCASE")
    fun deletePath(path: String)

    @Query("DELETE FROM media_metadata WHERE LOWER(full_path) = LOWER(:path) AND full_path != :path")
    fun deletePathCaseAliases(path: String)

    /**
     * SET expressions see the old row, so the CASE compares the stored mtime with [previousModified]: when they differ the file
     * changed outside this write and a non-manual capture date is marked pending.
     */
    @Query(
        "UPDATE media_metadata SET rating = :rating, tags = :tags, " +
            "date_taken_source = CASE WHEN last_modified != :previousModified AND date_taken_source != :manualSource " +
            "THEN :pendingSource ELSE date_taken_source END, " +
            "last_modified = :lastModified " +
            "WHERE full_path = :path COLLATE NOCASE"
    )
    fun updateXmpFields(
        path: String,
        rating: Int,
        tags: String,
        lastModified: Long,
        previousModified: Long,
        manualSource: Int,
        pendingSource: Int
    ): Int

    @Query(
        "UPDATE media_metadata SET date_taken = :dateTaken, date_taken_source = :dateTakenSource, last_fixed = :lastFixed " +
            "WHERE full_path = :path COLLATE NOCASE"
    )
    fun updateDateTakenFields(path: String, dateTaken: Long, dateTakenSource: Int, lastFixed: Int): Int

    /** Keeps the capture date columns. [previousModified] is the file mtime before the write that produced [lastModified]. */
    @Transaction
    fun upsertXmp(
        path: String,
        filename: String,
        parentPath: String,
        rating: Int,
        tags: String,
        lastModified: Long,
        previousModified: Long
    ) {
        deletePathCaseAliases(path)
        val updated = updateXmpFields(
            path, rating, tags, lastModified, previousModified, DATE_TAKEN_SOURCE_MANUAL, DATE_TAKEN_SOURCE_PENDING
        )
        if (updated == 0) {
            insertIgnore(
                MediaMetadata(
                    id = null,
                    fullPath = path,
                    filename = filename,
                    parentPath = parentPath,
                    lastModified = lastModified,
                    dateTaken = 0L,
                    dateTakenSource = DATE_TAKEN_SOURCE_PENDING,
                    lastFixed = 0,
                    rating = rating,
                    tags = tags
                )
            )
        }
    }

    /** Keeps rating, tags, and last_modified; a new row gets last_modified = 0 so the indexer still reads its XMP. */
    @Transaction
    fun upsertManualDateTaken(
        path: String,
        filename: String,
        parentPath: String,
        dateTaken: Long,
        dateTakenSource: Int,
        lastFixed: Int
    ) {
        if (updateDateTakenFields(path, dateTaken, dateTakenSource, lastFixed) == 0) {
            insertIgnore(
                MediaMetadata(
                    id = null,
                    fullPath = path,
                    filename = filename,
                    parentPath = parentPath,
                    lastModified = 0L,
                    dateTaken = dateTaken,
                    dateTakenSource = dateTakenSource,
                    lastFixed = lastFixed,
                    rating = 0,
                    tags = ""
                )
            )
        }
    }
}
