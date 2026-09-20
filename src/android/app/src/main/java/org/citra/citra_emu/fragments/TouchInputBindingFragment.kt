// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.FragmentTouchInputBinding
import org.citra.citra_emu.features.touchinput.TouchInputBinding
import org.citra.citra_emu.features.touchinput.TouchInputBindingAdapter
import org.citra.citra_emu.features.touchinput.TouchInputBindingManager
import org.citra.citra_emu.features.touchinput.TouchInputBindingProfileManager
import java.util.Locale
import kotlin.math.roundToInt

class TouchInputBindingFragment : Fragment() {
    private var _binding: FragmentTouchInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileManager: TouchInputBindingProfileManager
    private lateinit var bindingAdapter: TouchInputBindingAdapter

    private var currentProfile = TouchInputBindingProfileManager.DEFAULT_PROFILE
    private var selectedBinding: TouchInputBinding? = null

    // The fragment's root never changes; its content is re-inflated when the configuration does
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
            // Fallback for hosts that don't pass configuration changes on to fragments
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
     * The host activity handles rotation and light/dark changes itself, so the view is not
     * rebuilt automatically. Re-inflate it to pick up the matching layout and colors.
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
        setupBindingList()
        setupProfilePicker()

        binding.profileMenuButton.setOnClickListener { showProfileMenu(it) }
        binding.deleteAllButton.setOnClickListener { showDeleteAllDialog() }
        binding.touchInputBindingView.onTouchPointSelected = { x, y ->
            selectBinding(null)
            showBindSheet(x, y)
        }

        loadCurrentProfile()
    }

    private fun setupBindingList() {
        bindingAdapter = TouchInputBindingAdapter(
            onRowClicked = { selectBinding(if (it == selectedBinding) null else it) },
            onEditClicked = { showEditBindingDialog(it) },
            onDeleteClicked = { showDeleteBindingDialog(it) }
        )

        binding.bindingList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bindingAdapter
        }
    }

    private fun setupResultListeners() {
        val onBindingsChanged = { _: String, _: Bundle -> commitChanges() }

        parentFragmentManager.setFragmentResultListener(
            TouchInputBindingBottomSheetDialogFragment.RESULT_BINDING_ADDED,
            viewLifecycleOwner,
            onBindingsChanged
        )
        parentFragmentManager.setFragmentResultListener(
            RESULT_BINDING_REMOVED,
            viewLifecycleOwner,
            onBindingsChanged
        )
        parentFragmentManager.setFragmentResultListener(
            TouchInputBindingBottomSheetDialogFragment.RESULT_BINDING_CANCELLED,
            viewLifecycleOwner
        ) { _, _ -> binding.touchInputBindingView.clearSelection() }
    }

    private fun setupProfilePicker() {
        updateProfileName()
        binding.profileCard.setOnClickListener { showProfilePicker(it) }
    }

    private fun updateProfileName() {
        binding.profileName.text = currentProfile
    }

    /** Lists every profile, with the current one checked, plus an item to create a new one. */
    private fun showProfilePicker(anchor: View) {
        val profiles = profileManager.getProfiles()

        PopupMenu(requireContext(), anchor, Gravity.START).apply {
            profiles.forEachIndexed { index, name ->
                menu.add(GROUP_PROFILES, MENU_PROFILE_BASE + index, index, name)
            }
            menu.setGroupCheckable(GROUP_PROFILES, true, true)
            menu.findItem(MENU_PROFILE_BASE + profiles.indexOf(currentProfile))?.isChecked = true
            menu.add(Menu.NONE, MENU_CREATE_PROFILE, profiles.size, R.string.create_profile)

            setOnMenuItemClickListener { item ->
                if (item.itemId == MENU_CREATE_PROFILE) {
                    showCreateProfileDialog()
                } else {
                    val profile = profiles.getOrNull(item.itemId - MENU_PROFILE_BASE)
                        ?: return@setOnMenuItemClickListener false
                    if (profile != currentProfile) {
                        selectProfile(profile)
                        updateProfileName()
                    }
                }
                true
            }
            show()
        }
    }

    private fun selectProfile(profileName: String) {
        currentProfile = profileName
        profileManager.setCurrentProfile(profileName)
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        TouchInputBindingManager.setBindings(profileManager.loadProfile(currentProfile))
        refreshBindings()
    }

    /** Saves the active bindings to the current profile and updates the UI. */
    private fun commitChanges() {
        profileManager.saveProfile(currentProfile, TouchInputBindingManager.getBindings())
        refreshBindings()
    }

    private fun refreshBindings() {
        val bindings = TouchInputBindingManager.getBindings()
        val isEmpty = bindings.isEmpty()

        binding.touchInputBindingView.setBindings(bindings)
        bindingAdapter.submitList(bindings)

        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.bindingList.visibility = if (isEmpty) View.GONE else View.VISIBLE
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

    private fun showProfileMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(Menu.NONE, MENU_RENAME_PROFILE, 1, R.string.rename_profile)
            menu.add(Menu.NONE, MENU_DELETE_PROFILE, 2, R.string.delete_profile)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_RENAME_PROFILE -> showRenameProfileDialog()
                    MENU_DELETE_PROFILE -> showDeleteProfileDialog()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

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
                    updateProfileName()
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
                    updateProfileName()
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
                    updateProfileName()
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
            com.google.android.material.R.attr.textInputOutlinedStyle
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

    /** Accepts "0.5" or "0,5"; returns null unless the value is within 0..1. */
    private fun parseCoordinate(text: String): Float? =
        text.replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..1f }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun showToast(@StringRes message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val BIND_SHEET_TAG = "TouchInputBindingBottomSheet"
        private const val RESULT_BINDING_REMOVED = "touch_binding_removed"

        private const val TEXT_FIELD_CORNER_RADIUS_DP = 28
        private const val DISABLED_ALPHA = 0.38f

        private const val MENU_CREATE_PROFILE = Menu.FIRST
        private const val MENU_RENAME_PROFILE = Menu.FIRST + 1
        private const val MENU_DELETE_PROFILE = Menu.FIRST + 2

        // Profile items in the picker use ids from MENU_PROFILE_BASE upwards
        private const val GROUP_PROFILES = 1
        private const val MENU_PROFILE_BASE = 100
    }
}
