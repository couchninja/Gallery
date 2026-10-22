package org.fossify.gallery.dialogs

import com.google.android.material.checkbox.MaterialCheckBox
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
    partialTags: List<String> = emptyList(),
    onTags: (tags: List<String>) -> Unit,
    onBulkTags: ((addToAll: List<String>, removeFromAll: List<String>) -> Unit)? = null,
) {
    private val binding = DialogEditXmpTagsBinding.inflate(activity.layoutInflater)
    private val isBulkSelection = onBulkTags != null
    private val tagsOnAll = normalizeXmpTags(initialTags).toSet()
    private val tagsOnSome = normalizeXmpTags(partialTags).toSet()
    private val tagsOnAny = tagsOnAll + tagsOnSome
    private val selectedTags = LinkedHashSet(tagsOnAll)
    private val tagStates = linkedMapOf<String, TagCheckState>()
    private var displayedTags = buildDisplayedTagList()
    private var bindingCheckboxes = false

    init {
        if (isBulkSelection) {
            initBulkTagStates()
        }
        rebuildCheckboxes(activity)
        binding.editXmpTagsAddButton.setOnClickListener {
            addTagFromInput(activity)
        }
        binding.editXmpTagsInput.setOnEditorActionListener { _, _, _ ->
            addTagFromInput(activity)
            true
        }

        var committed = false
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                committed = true
                commit(onTags, onBulkTags)
            }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.edit_xmp_tags) { alertDialog ->
                    alertDialog.setOnDismissListener {
                        if (!committed) {
                            committed = true
                            commit(onTags, onBulkTags)
                        }
                    }
                }
            }
    }

    private enum class TagCheckState {
        OFF,
        PARTIAL,
        ON,
    }

    private fun initBulkTagStates() {
        displayedTags.forEach { tag ->
            tagStates[tag] = when {
                tagsOnAll.contains(tag) -> TagCheckState.ON
                tagsOnSome.contains(tag) -> TagCheckState.PARTIAL
                else -> TagCheckState.OFF
            }
        }
    }

    private fun commit(
        onTags: (List<String>) -> Unit,
        onBulkTags: ((addToAll: List<String>, removeFromAll: List<String>) -> Unit)?,
    ) {
        if (onBulkTags != null) {
            val addToAll = tagStates.filterValues { it == TagCheckState.ON }.keys.toList()
            val removeFromAll = tagStates.filter { (tag, state) ->
                state == TagCheckState.OFF && tagsOnAny.contains(tag)
            }.keys.toList()
            onBulkTags(addToAll, removeFromAll)
        } else {
            onTags(normalizeXmpTags(selectedTags.toList()))
        }
    }

    private fun buildDisplayedTagList(): List<String> {
        val merged = LinkedHashSet<String>()
        XmpTagIndex.getAllTagNamesSorted().forEach { merged.add(it) }
        tagsOnAll.forEach { merged.add(it) }
        tagsOnSome.forEach { merged.add(it) }
        selectedTags.forEach { merged.add(it) }
        return merged.sortedBy { it.lowercase() }
    }

    private fun rebuildCheckboxes(activity: BaseSimpleActivity) {
        binding.editXmpTagsCheckboxHolder.removeAllViews()
        displayedTags = buildDisplayedTagList()
        if (isBulkSelection) {
            displayedTags.forEach { tag ->
                if (!tagStates.containsKey(tag)) {
                    tagStates[tag] = TagCheckState.OFF
                }
            }
        }
        displayedTags.forEach { tag ->
            if (isBulkSelection) {
                binding.editXmpTagsCheckboxHolder.addView(createBulkCheckbox(activity, tag))
            } else {
                binding.editXmpTagsCheckboxHolder.addView(createSingleCheckbox(activity, tag))
            }
        }
    }

    private fun createSingleCheckbox(activity: BaseSimpleActivity, tag: String): MyAppCompatCheckbox {
        return MyAppCompatCheckbox(activity).apply {
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
    }

    private fun createBulkCheckbox(activity: BaseSimpleActivity, tag: String): MaterialCheckBox {
        return MaterialCheckBox(activity).apply {
            text = tag
            bindingCheckboxes = true
            checkedState = when (tagStates[tag]) {
                TagCheckState.ON -> MaterialCheckBox.STATE_CHECKED
                TagCheckState.PARTIAL -> MaterialCheckBox.STATE_INDETERMINATE
                TagCheckState.OFF, null -> MaterialCheckBox.STATE_UNCHECKED
            }
            bindingCheckboxes = false
            addOnCheckedStateChangedListener { _, state ->
                if (bindingCheckboxes) {
                    return@addOnCheckedStateChangedListener
                }
                tagStates[tag] = when (state) {
                    MaterialCheckBox.STATE_CHECKED -> TagCheckState.ON
                    MaterialCheckBox.STATE_INDETERMINATE -> TagCheckState.PARTIAL
                    else -> TagCheckState.OFF
                }
            }
        }
    }

    private fun addTagFromInput(activity: BaseSimpleActivity) {
        val tag = binding.editXmpTagsInput.text?.toString()?.trim().orEmpty()
        if (tag.isEmpty()) {
            return
        }
        if (isBulkSelection) {
            tagStates[tag] = TagCheckState.ON
        } else {
            selectedTags.add(tag)
        }
        binding.editXmpTagsInput.setText("")
        rebuildCheckboxes(activity)
    }
}
