package org.fossify.gallery

import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.LoadTimer
import org.fossify.gallery.helpers.MediaMetadataIndexer
import org.fossify.gallery.helpers.ThumbnailGridViewConfig
import org.fossify.gallery.helpers.ThumbnailGridViewShortcuts
import org.fossify.gallery.helpers.XmpTagIndex

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        LoadTimer.resetSession("appColdStart")
        ThumbnailGridViewConfig.resetAllPhotosGridToAll(this)
        ThumbnailGridViewShortcuts.refresh(this)
        ensureBackgroundThread {
            XmpTagIndex.loadFromDb(this)
            MediaMetadataIndexer.schedule(this)
        }
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
