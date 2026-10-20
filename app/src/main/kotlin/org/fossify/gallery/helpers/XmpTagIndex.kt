package org.fossify.gallery.helpers

import android.content.Context
import org.fossify.gallery.extensions.xmpMetadataDB
import org.fossify.gallery.models.XmpMetadataEntry

/** In-memory reverse map from XMP tag to the paths of photos carrying it, mirrored from the XMP metadata cache. */
object XmpTagIndex {
    private val pathsByTag = HashMap<String, LinkedHashSet<String>>()
    private val tagsByPath = HashMap<String, List<String>>()

    fun loadFromDb(context: Context) {
        val entries = try {
            context.xmpMetadataDB.getAll()
        } catch (_: Exception) {
            return
        }
        replaceAll(entries)
    }

    @Synchronized
    fun replaceAll(entries: Collection<XmpMetadataEntry>) {
        pathsByTag.clear()
        tagsByPath.clear()
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
        return tagsByPath.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value.orEmpty()
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
        tagsByPath[path] = tags
        tags.forEach { tag -> pathsByTag.getOrPut(tag) { LinkedHashSet() }.add(path) }
    }

    private fun removeLocked(path: String) {
        tagsByPath.remove(path)?.forEach { tag ->
            val paths = pathsByTag[tag] ?: return@forEach
            paths.remove(path)
            if (paths.isEmpty()) {
                pathsByTag.remove(tag)
            }
        }
    }
}
