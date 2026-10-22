package org.fossify.gallery.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.adapters.ThumbnailGridTagsAdapter
import org.fossify.gallery.databinding.ActivityThumbnailGridTagsBinding
import org.fossify.gallery.helpers.ACTION_MEDIA_METADATA_INDEX_UPDATED
import org.fossify.gallery.helpers.EXTRA_METADATA_XMP_CHANGED
import org.fossify.gallery.helpers.MediaMetadataIndexer
import org.fossify.gallery.helpers.THUMBNAIL_GRID_SELECTED_TAG
import org.fossify.gallery.helpers.XmpTagIndex

class ThumbnailGridTagsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityThumbnailGridTagsBinding::inflate)
    private var adapter: ThumbnailGridTagsAdapter? = null
    private var indexUpdateReceived = false
    private var receiverRegistered = false

    private val indexReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra(EXTRA_METADATA_XMP_CHANGED, false) != true) {
                return
            }
            indexUpdateReceived = true
            refreshTags()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.thumbnailGridTagsAppbar),
            padBottomSystem = listOf(binding.thumbnailGridTagsList),
        )
        setupMaterialScrollListener(binding.thumbnailGridTagsList, binding.thumbnailGridTagsAppbar)
        binding.thumbnailGridTagsPlaceholder.setTextColor(getProperTextColor())
        adapter = ThumbnailGridTagsAdapter(this, emptyList()) { tag -> finishWithTag(tag) }
        binding.thumbnailGridTagsList.adapter = adapter
        registerIndexReceiver()
        warmTagIndexFromDb()
        MediaMetadataIndexer.schedule(applicationContext)
        refreshTags()
        binding.root.postDelayed({
            if (!indexUpdateReceived) {
                indexUpdateReceived = true
                refreshTags()
            }
        }, LOADING_EMPTY_FALLBACK_MS)
    }

    override fun onDestroy() {
        unregisterIndexReceiver()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.thumbnailGridTagsAppbar, NavigationIcon.Arrow)
    }

    private fun registerIndexReceiver() {
        if (receiverRegistered) {
            return
        }
        val filter = IntentFilter(ACTION_MEDIA_METADATA_INDEX_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(indexReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(indexReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterIndexReceiver() {
        if (!receiverRegistered) {
            return
        }
        unregisterReceiver(indexReceiver)
        receiverRegistered = false
    }

    private fun warmTagIndexFromDb() {
        ensureBackgroundThread {
            XmpTagIndex.loadFromDb(applicationContext)
            runOnUiThread { refreshTags() }
        }
    }

    private fun refreshTags() {
        val tags = XmpTagIndex.getAllTagNamesSorted()
        adapter?.updateTags(tags)
        binding.thumbnailGridTagsList.beVisibleIf(tags.isNotEmpty())
        binding.thumbnailGridTagsPlaceholder.beVisibleIf(tags.isEmpty())
        if (tags.isEmpty()) {
            binding.thumbnailGridTagsPlaceholder.text = getString(
                if (indexUpdateReceived) {
                    R.string.thumbnail_grid_view_tags_empty
                } else {
                    R.string.thumbnail_grid_view_tags_loading
                },
            )
        }
    }

    private fun finishWithTag(tag: String) {
        setResult(
            RESULT_OK,
            Intent().putExtra(THUMBNAIL_GRID_SELECTED_TAG, tag),
        )
        finish()
    }

    companion object {
        private const val LOADING_EMPTY_FALLBACK_MS = 2500L
    }
}
