package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.mediaMetadataDB
import org.fossify.gallery.models.MediaMetadata
import java.util.Locale

/** In-memory reverse map from XMP tag to the paths of photos carrying it, mirrored from the metadata cache. */
object XmpTagIndex {
    private val pathsByTag = HashMap<String, LinkedHashSet<String>>()
    private val tagsByPath = HashMap<String, List<String>>()
    private val keyByLowercasePath = HashMap<String, String>()

    fun loadFromDb(context: Context) {
        val entries = try {
            context.mediaMetadataDB.getAll()
        } catch (_: Exception) {
            return
        }
        replaceAll(entries)
    }

    @Synchronized
    fun replaceAll(entries: Collection<MediaMetadata>) {
        pathsByTag.clear()
        tagsByPath.clear()
        keyByLowercasePath.clear()
        entries.forEach { putLocked(it.fullPath, it.tagList()) }
    }

    @Synchronized
    fun update(path: String, tags: List<String>) {
        removeLocked(path)
        putLocked(path, tags)
    }

    @Synchronized
    fun remove(path: String) {
        removeLocked(path)
    }

    @Synchronized
    fun getTags(path: String): List<String> {
        tagsByPath[path]?.let { return it }
        val key = keyByLowercasePath[path.lowercaseKey()] ?: return emptyList()
        return tagsByPath[key].orEmpty()
    }

    @Synchronized
    fun getPathsWithTag(tag: String): Set<String> = pathsByTag[tag]?.toSet().orEmpty()

    @Synchronized
    fun getAllTags(): Map<String, Set<String>> = pathsByTag.mapValues { it.value.toSet() }

    @Synchronized
    fun getAllTagNamesSorted(): List<String> = pathsByTag.keys.sortedBy { it.lowercase() }

    private fun putLocked(path: String, tags: List<String>) {
        if (tags.isEmpty()) {
            return
        }
        val key = pathKey(path)
        tagsByPath[key] = tags
        keyByLowercasePath[key.lowercaseKey()] = key
        tags.forEach { tag -> pathsByTag.getOrPut(tag) { LinkedHashSet() }.add(key) }
    }

    private fun removeLocked(path: String) {
        val key = keyByLowercasePath.remove(path.lowercaseKey()) ?: return
        tagsByPath.remove(key)?.forEach { tag ->
            val paths = pathsByTag[tag] ?: return@forEach
            paths.remove(key)
            if (paths.isEmpty()) {
                pathsByTag.remove(tag)
            }
        }
    }

    private fun pathKey(path: String): String = keyByLowercasePath[path.lowercaseKey()] ?: path

    private fun String.lowercaseKey(): String = lowercase(Locale.ROOT)
}
