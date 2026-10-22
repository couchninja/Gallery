package org.fossify.gallery.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.fossify.gallery.interfaces.*
import org.fossify.gallery.models.*

@Database(entities = [Directory::class, Medium::class, Widget::class, Favorite::class, MediaMetadata::class], version = 14)
abstract class GalleryDatabase : RoomDatabase() {

    abstract fun DirectoryDao(): DirectoryDao

    abstract fun MediumDao(): MediumDao

    abstract fun WidgetsDao(): WidgetsDao

    abstract fun FavoritesDao(): FavoritesDao

    abstract fun MediaMetadataDao(): MediaMetadataDao

    companion object {
        private var db: GalleryDatabase? = null

        fun getInstance(context: Context): GalleryDatabase {
            if (db == null) {
                synchronized(GalleryDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, GalleryDatabase::class.java, "gallery.db")
                            .fallbackToDestructiveMigration()
                            .addMigrations(MIGRATION_4_5)
                            .addMigrations(MIGRATION_5_6)
                            .addMigrations(MIGRATION_6_7)
                            .addMigrations(MIGRATION_7_8)
                            .addMigrations(MIGRATION_8_9)
                            .addMigrations(MIGRATION_9_10)
                            .addMigrations(MIGRATION_10_11)
                            .addMigrations(MIGRATION_11_12)
                            .addMigrations(MIGRATION_12_13)
                            .addMigrations(MIGRATION_13_14)
                            .build()
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            if (db?.isOpen == true) {
                db?.close()
            }
            db = null
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN video_duration INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `widgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `widget_id` INTEGER NOT NULL, `folder_path` TEXT NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_widgets_widget_id` ON `widgets` (`widget_id`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `date_takens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL, `date_taken` INTEGER NOT NULL, `last_fixed` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorites` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `full_path` TEXT NOT NULL, `filename` TEXT NOT NULL, `parent_path` TEXT NOT NULL)")

                database.execSQL("CREATE UNIQUE INDEX `index_date_takens_full_path` ON `date_takens` (`full_path`)")
                database.execSQL("CREATE UNIQUE INDEX `index_favorites_full_path` ON `favorites` (`full_path`)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE directories ADD COLUMN sort_value TEXT default '' NOT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE date_takens ADD COLUMN last_modified INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN media_store_id INTEGER default 0 NOT NULL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exif_ratings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`full_path` TEXT NOT NULL, " +
                        "`filename` TEXT NOT NULL, " +
                        "`parent_path` TEXT NOT NULL, " +
                        "`rating` INTEGER NOT NULL, " +
                        "`last_modified` INTEGER NOT NULL)"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exif_ratings_full_path` ON `exif_ratings` (`full_path`)")
            }
        }

        // last_modified = 0 forces the indexer to re-read every JPEG so tags get filled in.
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xmp_metadata` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`full_path` TEXT NOT NULL, " +
                        "`filename` TEXT NOT NULL, " +
                        "`parent_path` TEXT NOT NULL, " +
                        "`rating` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, " +
                        "`last_modified` INTEGER NOT NULL)"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_xmp_metadata_full_path` ON `xmp_metadata` (`full_path`)")
                database.execSQL(
                    "INSERT OR REPLACE INTO `xmp_metadata` (`full_path`, `filename`, `parent_path`, `rating`, `tags`, `last_modified`) " +
                        "SELECT `full_path`, `filename`, `parent_path`, `rating`, '', 0 FROM `exif_ratings`"
                )
                database.execSQL("DROP TABLE IF EXISTS `exif_ratings`")
            }
        }

        // Before this version the indexer marked its rows with last_fixed = 0; manual fixes stored a timestamp.
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE date_takens ADD COLUMN source INTEGER default 0 NOT NULL")
                database.execSQL("UPDATE date_takens SET source = 1 WHERE last_fixed = 0")
            }
        }

        // XMP rows start with date_taken_source = 3 (pending) so the indexer reads their EXIF date once. JPEG rows that only had a
        // date get last_modified = 0 so their XMP is read; the indexer keeps manual dates.
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media_metadata` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`full_path` TEXT NOT NULL, " +
                        "`filename` TEXT NOT NULL, " +
                        "`parent_path` TEXT NOT NULL, " +
                        "`last_modified` INTEGER NOT NULL, " +
                        "`date_taken` INTEGER NOT NULL, " +
                        "`date_taken_source` INTEGER NOT NULL, " +
                        "`last_fixed` INTEGER NOT NULL, " +
                        "`rating` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL)"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_metadata_full_path` ON `media_metadata` (`full_path`)")
                database.execSQL(
                    "INSERT INTO `media_metadata` (`full_path`, `filename`, `parent_path`, `last_modified`, `date_taken`, `date_taken_source`, `last_fixed`, `rating`, `tags`) " +
                        "SELECT `full_path`, `filename`, `parent_path`, `last_modified`, 0, 3, 0, `rating`, `tags` FROM `xmp_metadata`"
                )
                database.execSQL(
                    "UPDATE `media_metadata` SET " +
                        "`date_taken` = (SELECT d.`date_taken` FROM `date_takens` d WHERE d.`full_path` = `media_metadata`.`full_path`), " +
                        "`date_taken_source` = (SELECT d.`source` FROM `date_takens` d WHERE d.`full_path` = `media_metadata`.`full_path`), " +
                        "`last_fixed` = (SELECT d.`last_fixed` FROM `date_takens` d WHERE d.`full_path` = `media_metadata`.`full_path`) " +
                        "WHERE EXISTS (SELECT 1 FROM `date_takens` d WHERE d.`full_path` = `media_metadata`.`full_path`)"
                )
                database.execSQL(
                    "INSERT INTO `media_metadata` (`full_path`, `filename`, `parent_path`, `last_modified`, `date_taken`, `date_taken_source`, `last_fixed`, `rating`, `tags`) " +
                        "SELECT d.`full_path`, d.`filename`, d.`parent_path`, " +
                        "CASE WHEN LOWER(d.`full_path`) LIKE '%.jpg' OR LOWER(d.`full_path`) LIKE '%.jpeg' THEN 0 ELSE d.`last_modified` END, " +
                        "d.`date_taken`, d.`source`, d.`last_fixed`, 0, '' " +
                        "FROM `date_takens` d WHERE NOT EXISTS (" +
                        "SELECT 1 FROM `media_metadata` m WHERE m.`full_path` = d.`full_path`)"
                )
                database.execSQL("DROP TABLE IF EXISTS `date_takens`")
                database.execSQL("DROP TABLE IF EXISTS `xmp_metadata`")
            }
        }
    }
}
