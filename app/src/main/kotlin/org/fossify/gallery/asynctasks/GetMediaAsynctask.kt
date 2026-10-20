package org.fossify.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.helpers.DateTakenIndexer
import org.fossify.gallery.helpers.XmpMetadataIndexer
import org.fossify.gallery.helpers.*
import org.fossify.gallery.R
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, private val onStatus: ((String) -> Unit)? = null,
    val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    private val mediaFetcher = MediaFetcher(context)
    private var shouldIndexCaptureTimes = false

    private fun reportStatus(stringRes: Int) {
        onStatus?.invoke(context.getString(stringRes))
    }

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        val getProperDateTaken = folderSorting and SORT_BY_DATE_TAKEN != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_DAILY != 0 ||
            folderGrouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0
        shouldIndexCaptureTimes = getProperDateTaken

        val getProperLastModified = folderSorting and SORT_BY_DATE_MODIFIED != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 ||
            folderGrouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 ||
            getProperDateTaken

        val getProperFileSize = folderSorting and SORT_BY_SIZE != 0
        val favoritePaths = context.getFavoritePaths()
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val lastModifieds = if (getProperLastModified) mediaFetcher.getLastModifieds() else HashMap()
        if (getProperDateTaken) {
            reportStatus(R.string.loading_media_reading_dates)
        }
        val dateTakens = if (getProperDateTaken) mediaFetcher.getDateTakens() else HashMap()
        val exifRatings = if (context.config.showExifRatings) {
            reportStatus(R.string.loading_media_reading_ratings)
            if (showAll) {
                mediaFetcher.getExifRatings()
            } else {
                mediaFetcher.getFolderExifRatings(mPath)
            }
        } else {
            HashMap()
        }

        val media = if (showAll) {
            reportStatus(R.string.loading_media_finding_folders)
            val foldersToScan = mediaFetcher.getFoldersToScan().filter { it != RECYCLE_BIN && it != FAVORITES && !context.config.isFolderProtected(it) }
            reportStatus(R.string.loading_media_scanning_library)
            val media = ArrayList<Medium>()
            foldersToScan.forEach {
                val newMedia = mediaFetcher.getFilesFrom(
                    it, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens.clone() as HashMap<String, Long>,
                    exifRatings, null
                )
                media.addAll(newMedia)
            }

            reportStatus(R.string.loading_media_organizing)
            mediaFetcher.sortMedia(media, context.config.getFolderSorting(SHOW_ALL))
            media
        } else {
            reportStatus(R.string.loading_media_scanning_library)
            mediaFetcher.getFilesFrom(
                mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                getVideoDurations, lastModifieds, dateTakens, exifRatings, null
            )
        }

        reportStatus(R.string.loading_media_organizing)
        return mediaFetcher.groupMedia(media, pathToUse)
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        callback(media)
        if (shouldIndexCaptureTimes) {
            DateTakenIndexer.schedule(context)
        }
        if (context.config.showExifRatings) {
            XmpMetadataIndexer.schedule(context)
        }
    }

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}
