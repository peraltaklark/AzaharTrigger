// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.roundToInt
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.FragmentTouchInputBinding
import org.citra.citra_emu.databinding.ItemProfilePickerRowBinding
import org.citra.citra_emu.databinding.PopupProfilePickerBinding
import org.citra.citra_emu.features.touchinput.TouchInputBinding
import org.citra.citra_emu.features.touchinput.TouchInputBindingAdapter
import org.citra.citra_emu.features.touchinput.TouchInputBindingManager
import org.citra.citra_emu.features.touchinput.TouchInputBindingProfileManager
import com.google.android.material.R as MaterialR

class TouchInputBindingFragment : Fragment() {
    private var _binding: FragmentTouchInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileManager: TouchInputBindingProfileManager
    private lateinit var bindingAdapter: TouchInputBindingAdapter

    private var currentProfile = TouchInputBindingProfileManager.DEFAULT_PROFILE
    private var selectedBinding: TouchInputBinding? = null

    // The root never changes; its content is inflated again when the configuration does
    private var contentHost: FrameLayout? = null
    private var inflatedOrientation = Configuration.ORIENTATION_UNDEFINED
    private var inflatedNightMode = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val host = FrameLayout(inflater.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Fallback for hosts that don't pass configuration changes on to their fragments
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                post { rebuildContentIfNeeded() }
            }
        }
        contentHost = host
        inflateContent()
        return host
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        profileManager = TouchInputBindingProfileManager(requireContext())
        currentProfile = profileManager.getCurrentProfile()

        setupResultListeners()
        bindViews()
    }

    override fun onResume() {
        super.onResume()
        refreshBindings()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rebuildContentIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        contentHost = null
    }

    private fun inflateContent() {
        val host = contentHost ?: return
        host.removeAllViews()
        _binding = FragmentTouchInputBinding.inflate(LayoutInflater.from(host.context), host, true)

        val config = resources.configuration
        inflatedOrientation = config.orientation
        inflatedNightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    /**
     * The host activity handles rotation and light/dark changes itself, so the view isn't rebuilt
     * automatically. Inflating it again picks up the matching layout and colors.
     */
    private fun rebuildContentIfNeeded() {
        if (_binding == null || !isAdded) return

        val config = resources.configuration
        val nightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (config.orientation == inflatedOrientation && nightMode == inflatedNightMode) return

        inflateContent()
        bindViews()
    }

    private fun bindViews() {
        bindingAdapter = TouchInputBindingAdapter(
            onRowClicked = { selectBinding(if (it == selectedBinding) null else it) },
            onEditClicked = { showEditBindingDialog(it) },
            onDeleteClicked = { showDeleteBindingDialog(it) }
        )
        binding.bindingList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bindingAdapter
        }

        binding.profileCard.setOnClickListener { showProfilePicker(it) }
        binding.deleteAllButton.setOnClickListener { showDeleteAllDialog() }
        binding.touchInputBindingView.onTouchPointSelected = { x, y ->
            selectBinding(null)
            showBindSheet(x, y)
        }

        updateProfileChip()
        loadCurrentProfile()
    }

    private fun setupResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            TouchInputBindingBottomSheetDialogFragment.RESULT_BINDING_ADDED,
            viewLifecycleOwner
        ) { _, _ -> commitChanges() }

        parentFragmentManager.setFragmentResultListener(
            TouchInputBindingBottomSheetDialogFragment.RESULT_BINDING_CANCELLED,
            viewLifecycleOwner
        ) { _, _ -> binding.touchInputBindingView.clearSelection() }
    }

    private fun updateProfileChip() {
        binding.profileName.text = currentProfile
        binding.profileAvatar?.text = avatarLetterFor(currentProfile)
    }

    private fun selectProfile(profileName: String) {
        currentProfile = profileName
        profileManager.setCurrentProfile(profileName)
        updateProfileChip()
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        TouchInputBindingManager.setBindings(profileManager.loadProfile(currentProfile))
        refreshBindings()
    }

    /** Saves the active bindings to the current profile and updates the screen. */
    private fun commitChanges() {
        profileManager.saveProfile(currentProfile, TouchInputBindingManager.getBindings())
        refreshBindings()
    }

    private fun refreshBindings() {
        val bindings = TouchInputBindingManager.getBindings()
        val isEmpty = bindings.isEmpty()

        binding.touchInputBindingView.setBindings(bindings)
        bindingAdapter.submitList(bindings)

        binding.bindingCount.text = bindings.size.toString()
        binding.bindingCount.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.bindingsCard.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.deleteAllButton.isEnabled = !isEmpty
        binding.deleteAllButton.alpha = if (isEmpty) DISABLED_ALPHA else 1f

        if (selectedBinding !in bindings) {
            selectedBinding = null
        }
        selectBinding(selectedBinding)
    }

    /** Highlights [touchBinding] in the list and on the preview, or clears it if null. */
    private fun selectBinding(touchBinding: TouchInputBinding?) {
        selectedBinding = touchBinding

        val index = touchBinding?.let { TouchInputBindingManager.getBindings().indexOf(it) } ?: -1
        binding.touchInputBindingView.setHighlightedIndex(index)
        bindingAdapter.selectedBinding = touchBinding
    }

    private fun showBindSheet(x: Float, y: Float) {
        TouchInputBindingBottomSheetDialogFragment
            .newInstance(x, y)
            .show(parentFragmentManager, BIND_SHEET_TAG)
    }

    /**
     * Dropdown anchored below the profile chip: every profile, the current one checked, then
     * create, rename and delete for the current profile.
     */
    private fun showProfilePicker(anchor: View) {
        val popupBinding = PopupProfilePickerBinding.inflate(LayoutInflater.from(requireContext()))
        val container = popupBinding.profilePickerContent

        val popupWindow = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dpToPx(POPUP_ELEVATION_DP).toFloat()
        }

        profileManager.getProfiles().forEach { name ->
            container.addView(
                createProfileRow(name, selected = name == currentProfile) {
                    popupWindow.dismiss()
                    if (name != currentProfile) {
                        selectProfile(name)
                    }
                }
            )
        }

        container.addView(createDivider())

        container.addView(
            createActionRow(R.drawable.ic_add, getString(R.string.create_profile)) {
                popupWindow.dismiss()
                showCreateProfileDialog()
            }
        )
        container.addView(
            createActionRow(
                R.drawable.ic_edit,
                getString(R.string.profile_action_rename_format, currentProfile)
            ) {
                popupWindow.dismiss()
                showRenameProfileDialog()
            }
        )

        val canDelete = currentProfile != TouchInputBindingProfileManager.DEFAULT_PROFILE
        container.addView(
            createActionRow(
                R.drawable.ic_delete_outline,
                getString(R.string.profile_action_delete_format, currentProfile),
                destructive = true,
                enabled = canDelete
            ) {
                popupWindow.dismiss()
                showDeleteProfileDialog()
            }
        )

        popupWindow.showAsDropDown(anchor, 0, dpToPx(POPUP_OFFSET_DP))
    }

    private fun createProfileRow(name: String, selected: Boolean, onClick: () -> Unit): View {
        val rowBinding = ItemProfilePickerRowBinding.inflate(LayoutInflater.from(requireContext()))
        rowBinding.rowAvatar.visibility = View.VISIBLE
        rowBinding.rowAvatar.text = avatarLetterFor(name)
        rowBinding.rowLabel.text = name
        rowBinding.rowCheck.visibility = if (selected) View.VISIBLE else View.GONE
        rowBinding.root.setOnClickListener { onClick() }
        return rowBinding.root
    }

    private fun createActionRow(
        @DrawableRes iconRes: Int,
        label: String,
        destructive: Boolean = false,
        enabled: Boolean = true,
        onClick: () -> Unit
    ): View {
        val rowBinding = ItemProfilePickerRowBinding.inflate(LayoutInflater.from(requireContext()))
        val useErrorColor = destructive && enabled
        val iconColor = themeColor(
            if (useErrorColor) MaterialR.attr.colorError else MaterialR.attr.colorOnSurfaceVariant
        )
        val labelColor = if (useErrorColor) iconColor else themeColor(MaterialR.attr.colorOnSurface)

        rowBinding.rowIcon.visibility = View.VISIBLE
        rowBinding.rowIcon.setImageResource(iconRes)
        rowBinding.rowIcon.imageTintList = ColorStateList.valueOf(iconColor)
        rowBinding.rowLabel.text = label
        rowBinding.rowLabel.setTextColor(labelColor)

        rowBinding.root.isEnabled = enabled
        rowBinding.root.alpha = if (enabled) 1f else DISABLED_ALPHA
        rowBinding.root.setOnClickListener { if (enabled) onClick() }
        return rowBinding.root
    }

    private fun createDivider(): View =
        View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(4)
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }
            alpha = DIVIDER_ALPHA
            setBackgroundColor(themeColor(MaterialR.attr.colorOutline))
        }

    private fun avatarLetterFor(name: String): String =
        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    private fun themeColor(attr: Int): Int = MaterialColors.getColor(requireContext(), attr, 0)

    private fun showCreateProfileDialog() {
        val nameField = createTextField(R.string.profile_name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_new_profile)
            .setView(createDialogContent(nameField))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameField.text
                if (name.isEmpty()) return@setPositiveButton

                if (profileManager.createProfile(name)) {
                    selectProfile(name)
                } else {
                    showToast(R.string.profile_already_exists)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameProfileDialog() {
        val nameField = createTextField(R.string.profile_name, currentProfile)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile_name)
            .setView(createDialogContent(nameField))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = nameField.text
                if (newName.isEmpty() || newName == currentProfile) {
                    return@setPositiveButton
                }

                if (profileManager.renameProfile(currentProfile, newName)) {
                    currentProfile = newName
                    updateProfileChip()
                } else {
                    showToast(R.string.profile_name_already_exists)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        if (currentProfile == TouchInputBindingProfileManager.DEFAULT_PROFILE) {
            showToast(R.string.cannot_delete_default_profile)
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile)
            .setMessage(getString(R.string.delete_profile_confirm, currentProfile))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (profileManager.deleteProfile(currentProfile)) {
                    selectProfile(TouchInputBindingProfileManager.DEFAULT_PROFILE)
                    showToast(R.string.profile_deleted)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteAllDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all)
            .setMessage(R.string.delete_all_touch_input_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TouchInputBindingManager.clearBindings()
                commitChanges()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditBindingDialog(touchBinding: TouchInputBinding) {
        val decimalInput = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val xField = createTextField(
            R.string.x_coordinate,
            formatCoordinate(touchBinding.x),
            decimalInput
        )
        val yField = createTextField(
            R.string.y_coordinate,
            formatCoordinate(touchBinding.y),
            decimalInput
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit)
            .setView(createDialogContent(xField, yField))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val x = parseCoordinate(xField.text)
                val y = parseCoordinate(yField.text)
                if (x == null || y == null) {
                    showToast(R.string.invalid_coordinates)
                    return@setPositiveButton
                }

                TouchInputBindingManager.replaceBinding(
                    touchBinding,
                    touchBinding.copy(x = x, y = y)
                )
                commitChanges()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteBindingDialog(touchBinding: TouchInputBinding) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_touch_input_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TouchInputBindingManager.removeBinding(touchBinding)
                commitChanges()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun createTextField(
        @StringRes hintRes: Int,
        initialText: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT
    ): TextInputLayout {
        val cornerRadius = dpToPx(TEXT_FIELD_CORNER_RADIUS_DP).toFloat()

        val layout = TextInputLayout(
            requireContext(),
            null,
            MaterialR.attr.textInputOutlinedStyle
        ).apply {
            setHint(hintRes)
            setBoxCornerRadii(cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        }

        layout.addView(
            TextInputEditText(layout.context).apply {
                setText(initialText)
                this.inputType = inputType
            }
        )
        return layout
    }

    private fun createDialogContent(vararg fields: TextInputLayout): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), 0)

            fields.forEachIndexed { index, field ->
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = dpToPx(12)
                }
                addView(field, params)
            }
        }

    private val TextInputLayout.text: String
        get() = editText?.text?.toString()?.trim().orEmpty()

    private fun formatCoordinate(value: Float): String =
        String.format(Locale.US, "%.3f", value)

    /** Accepts "0.5" or "0,5", and returns null unless the value is between 0 and 1. */
    private fun parseCoordinate(text: String): Float? =
        text.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1f }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun showToast(@StringRes message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val BIND_SHEET_TAG = "TouchInputBindingBottomSheet"

        private const val TEXT_FIELD_CORNER_RADIUS_DP = 28
        private const val DISABLED_ALPHA = 0.38f
        private const val DIVIDER_ALPHA = 0.3f

        private const val POPUP_ELEVATION_DP = 8
        private const val POPUP_OFFSET_DP = 4
    }
}
