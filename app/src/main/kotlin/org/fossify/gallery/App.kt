package org.fossify.gallery

import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.DateTakenIndexer
import org.fossify.gallery.helpers.ThumbnailGridViewShortcuts
import org.fossify.gallery.helpers.XmpMetadataIndexer
import org.fossify.gallery.helpers.XmpTagIndex

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        ThumbnailGridViewShortcuts.refresh(this)
        ensureBackgroundThread {
            DateTakenIndexer.schedule(this)
            XmpTagIndex.loadFromDb(this)
            if (config.showExifRatings) {
                XmpMetadataIndexer.schedule(this)
            }
        }
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
