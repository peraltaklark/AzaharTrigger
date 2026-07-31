// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.os.Bundle
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
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

class TouchInputBindingFragment : Fragment() {

    private var _binding: FragmentTouchInputBinding? = null
    private val binding get() = _binding!!

    private lateinit var profileManager: TouchInputBindingProfileManager
    private lateinit var bindingAdapter: TouchInputBindingAdapter

    private var currentProfile = DEFAULT_PROFILE

    companion object {
        private const val DEFAULT_PROFILE = "Default"
        private const val MASK_COORDINATE_FORMAT = "%.3f"

        private const val MENU_CREATE_PROFILE = Menu.FIRST
        private const val MENU_RENAME_PROFILE = Menu.FIRST + 1
        private const val MENU_DELETE_PROFILE = Menu.FIRST + 2
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTouchInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        profileManager = TouchInputBindingProfileManager(requireContext())
        currentProfile = profileManager.getCurrentProfile()

        setupBindingRecyclerView()
        setupFragmentResultListener()
        setupProfileSpinner()
        setupProfileButtons()
        setupTouchInputView()

        loadCurrentProfile()
    }

    override fun onResume() {
        super.onResume()
        refreshBindingList()
    }

    // ----------------------------------------------------
    // RecyclerView
    // ----------------------------------------------------

    private fun setupBindingRecyclerView() {
        bindingAdapter = TouchInputBindingAdapter(
            onEditClicked = { showEditBindingDialog(it) },
            onDeleteClicked = { showDeleteBindingDialog(it) }
        )

        binding.bindingList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bindingAdapter
            setHasFixedSize(true)
        }
    }

    private fun refreshBindingList() {
        val bindings = TouchInputBindingManager.getBindings()

        binding.touchInputBindingView.setBindings(bindings)
        bindingAdapter.submitList(bindings)
    }

    // ----------------------------------------------------
    // Fragment Result
    // ----------------------------------------------------

    private fun setupFragmentResultListener() {
        val resultListener = { _: String, _: Bundle ->
            commitBindings()
            refreshBindingList()
        }

        parentFragmentManager.setFragmentResultListener(
            "touch_binding_added",
            viewLifecycleOwner,
            resultListener
        )

        parentFragmentManager.setFragmentResultListener(
            "touch_binding_removed",
            viewLifecycleOwner,
            resultListener
        )

        // Clear preview dot selection when user cancels/dismisses bottom sheet dialog
        parentFragmentManager.setFragmentResultListener(
            "touch_binding_cancelled",
            viewLifecycleOwner
        ) { _, _ ->
            binding.touchInputBindingView.clearSelection()
        }
    }

    // ----------------------------------------------------
    // Touch Input Preview
    // ----------------------------------------------------

    private fun setupTouchInputView() {
        binding.touchInputBindingView.onTouchPointSelected = { x, y ->
            showBindingDialog(x, y)
        }
    }

    // ----------------------------------------------------
    // Profiles
    // ----------------------------------------------------

    private fun selectProfile(profileName: String) {
        currentProfile = profileName
        profileManager.setCurrentProfile(currentProfile)
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        val bindings = profileManager.loadProfile(currentProfile)
        TouchInputBindingManager.setBindings(bindings)
        refreshBindingList()
    }

    private fun commitBindings() {
        profileManager.saveProfile(
            currentProfile,
            TouchInputBindingManager.getBindings()
        )
    }

    private fun setupProfileSpinner() {
        updateProfileSpinner()

        binding.profileSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val selectedProfile = parent?.getItemAtPosition(position)?.toString() ?: return

                    if (selectedProfile != currentProfile) {
                        selectProfile(selectedProfile)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    private fun updateProfileSpinner() {
        val profiles = profileManager.getProfiles()

        // Pass M3 Popup Theme Context so popup background matches theme container
        val popupContext = ContextThemeWrapper(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_PopupMenu
        )

        val adapter = ArrayAdapter(
            popupContext,
            android.R.layout.simple_spinner_item,
            profiles
        )

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.profileSpinner.adapter = adapter

        val index = profiles.indexOf(currentProfile)
        if (index >= 0) {
            binding.profileSpinner.setSelection(index)
        }
    }

    // ----------------------------------------------------
    // Profile Buttons
    // ----------------------------------------------------

    private fun setupProfileButtons() {
        binding.profileMenuButton.setOnClickListener { view ->
            showProfileMenu(view)
        }

        binding.deleteAllButton.setOnClickListener {
            showDeleteAllBindingsDialog()
        }
    }

    private fun showProfileMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)

        popup.menu.add(Menu.NONE, MENU_CREATE_PROFILE, 1, getString(R.string.create_profile))
        popup.menu.add(Menu.NONE, MENU_RENAME_PROFILE, 2, getString(R.string.rename_profile))
        popup.menu.add(Menu.NONE, MENU_DELETE_PROFILE, 3, getString(R.string.delete_profile))

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE_PROFILE -> {
                    showCreateProfileDialog()
                    true
                }

                MENU_RENAME_PROFILE -> {
                    showEditProfileDialog()
                    true
                }

                MENU_DELETE_PROFILE -> {
                    showDeleteProfileDialog()
                    true
                }

                else -> false
            }
        }

        popup.show()
    }

    // ----------------------------------------------------
    // Profile Dialogs
    // ----------------------------------------------------

    private fun showCreateProfileDialog() {
        val (inputLayout, inputEditText) = createInputField(
            hint = getString(R.string.profile_name)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_new_profile)
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = inputEditText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton

                if (profileManager.createProfile(name)) {
                    selectProfile(name)
                    updateProfileSpinner()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.profile_already_exists),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditProfileDialog() {
        val (inputLayout, inputEditText) = createInputField(
            hint = getString(R.string.profile_name),
            initialText = currentProfile
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile_name)
            .setView(inputLayout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = inputEditText.text.toString().trim()
                if (newName.isEmpty() || newName == currentProfile) return@setPositiveButton

                if (profileManager.renameProfile(currentProfile, newName)) {
                    currentProfile = newName
                    updateProfileSpinner()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.profile_name_already_exists),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteProfileDialog() {
        if (currentProfile == DEFAULT_PROFILE) {
            Toast.makeText(
                requireContext(),
                getString(R.string.cannot_delete_default_profile),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_profile)
            .setMessage(getString(R.string.delete_profile_confirm, currentProfile))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (profileManager.deleteProfile(currentProfile)) {
                    selectProfile(DEFAULT_PROFILE)
                    updateProfileSpinner()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.profile_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteAllBindingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all)
            .setMessage(R.string.delete_all_touch_input_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                TouchInputBindingManager.clearBindings()
                commitBindings()
                refreshBindingList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----------------------------------------------------
    // Binding Dialogs
    // ----------------------------------------------------

    private fun showBindingDialog(x: Float, y: Float) {
        TouchInputBindingBottomSheetDialogFragment
            .newInstance(x, y)
            .show(
                parentFragmentManager,
                "TouchInputBindingBottomSheet"
            )
    }

    private fun showEditBindingDialog(touchBinding: TouchInputBinding) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * density).toInt()
            setPadding(padding, (16 * density).toInt(), padding, 0)
        }

        val (xLayout, xInput) = createInputField(
            hint = getString(R.string.x_coordinate),
            initialText = formatCoordinate(touchBinding.x),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            addMarginBottom = true
        )

        val (yLayout, yInput) = createInputField(
            hint = getString(R.string.y_coordinate),
            initialText = formatCoordinate(touchBinding.y),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )

        container.addView(xLayout)
        container.addView(yLayout)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val x = xInput.text.toString().toFloatOrNull()
                val y = yInput.text.toString().toFloatOrNull()

                if (x != null && y != null && x in 0f..1f && y in 0f..1f) {
                    TouchInputBindingManager.removeBinding(touchBinding)
                    TouchInputBindingManager.addBinding(
                        touchBinding.copy(x = x, y = y)
                    )
                    commitBindings()
                    refreshBindingList()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.invalid_coordinates),
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
                commitBindings()
                refreshBindingList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------

    private fun createInputField(
        hint: String,
        initialText: String = "",
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        addMarginBottom: Boolean = false
    ): Pair<TextInputLayout, TextInputEditText> {
        val density = resources.displayMetrics.density

        val inputLayout = TextInputLayout(
            requireContext(),
            null,
            com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox
        ).apply {
            this.hint = hint
            if (addMarginBottom) {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (12 * density).toInt()
                }
            } else {
                val padding = (24 * density).toInt()
                setPadding(padding, (16 * density).toInt(), padding, 0)
            }
        }

        val editText = TextInputEditText(inputLayout.context).apply {
            setText(initialText)
            this.inputType = inputType
        }

        inputLayout.addView(editText)
        return Pair(inputLayout, editText)
    }

    private fun formatCoordinate(value: Float): String {
        return String.format(MASK_COORDINATE_FORMAT, value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
