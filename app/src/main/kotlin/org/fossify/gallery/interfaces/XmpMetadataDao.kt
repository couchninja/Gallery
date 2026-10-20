package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.XmpMetadataEntry

@Dao
interface XmpMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(entries: List<XmpMetadataEntry>)

    @Query("SELECT full_path, filename, parent_path, rating, tags, last_modified FROM xmp_metadata WHERE parent_path = :path COLLATE NOCASE")
    fun getFromPath(path: String): List<XmpMetadataEntry>

    @Query("SELECT full_path, filename, parent_path, rating, tags, last_modified FROM xmp_metadata")
    fun getAll(): List<XmpMetadataEntry>

    @Query("SELECT full_path, filename, parent_path, rating, tags, last_modified FROM xmp_metadata WHERE full_path = :path COLLATE NOCASE LIMIT 1")
    fun getByPath(path: String): XmpMetadataEntry?

    @Query("DELETE FROM xmp_metadata WHERE full_path = :path COLLATE NOCASE")
    fun deletePath(path: String)
}
