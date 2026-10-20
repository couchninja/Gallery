package org.fossify.gallery.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogEditXmpTagsBinding
import org.fossify.gallery.helpers.XmpTagIndex
import org.fossify.gallery.helpers.normalizeXmpTags

class EditXmpTagsDialog(
    activity: BaseSimpleActivity,
    initialTags: List<String>,
    val callback: (tags: List<String>) -> Unit,
) {
    private val binding = DialogEditXmpTagsBinding.inflate(activity.layoutInflater)
    private val selectedTags = LinkedHashSet(normalizeXmpTags(initialTags))
    private var displayedTags = buildDisplayedTagList()

    init {
        rebuildCheckboxes(activity)
        binding.editXmpTagsAddButton.setOnClickListener {
            addTagFromInput(activity)
        }
        binding.editXmpTagsInput.setOnEditorActionListener { _, _, _ ->
            addTagFromInput(activity)
            true
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                callback(normalizeXmpTags(selectedTags.toList()))
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.edit_xmp_tags)
            }
    }

    private fun buildDisplayedTagList(): List<String> {
        val merged = LinkedHashSet<String>()
        XmpTagIndex.getAllTagNamesSorted().forEach { merged.add(it) }
        selectedTags.forEach { merged.add(it) }
        return merged.sortedBy { it.lowercase() }
    }

    private fun rebuildCheckboxes(activity: BaseSimpleActivity) {
        binding.editXmpTagsCheckboxHolder.removeAllViews()
        displayedTags = buildDisplayedTagList()
        displayedTags.forEach { tag ->
            val checkbox = MyAppCompatCheckbox(activity).apply {
                text = tag
                isChecked = selectedTags.contains(tag)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedTags.add(tag)
                    } else {
                        selectedTags.remove(tag)
                    }
                }
            }
            binding.editXmpTagsCheckboxHolder.addView(checkbox)
        }
    }

    private fun addTagFromInput(activity: BaseSimpleActivity) {
        val tag = binding.editXmpTagsInput.text?.toString()?.trim().orEmpty()
        if (tag.isEmpty()) {
            return
        }
        selectedTags.add(tag)
        binding.editXmpTagsInput.setText("")
        rebuildCheckboxes(activity)
    }
}
