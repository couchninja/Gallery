package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.ExifRating

@Dao
interface ExifRatingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(ratings: List<ExifRating>)

    @Query("SELECT full_path, filename, parent_path, rating, last_modified FROM exif_ratings WHERE parent_path = :path COLLATE NOCASE")
    fun getRatingsFromPath(path: String): List<ExifRating>

    @Query("SELECT full_path, filename, parent_path, rating, last_modified FROM exif_ratings")
    fun getAllRatings(): List<ExifRating>

    @Query("DELETE FROM exif_ratings WHERE full_path = :path COLLATE NOCASE")
    fun deleteRatingPath(path: String)
}
