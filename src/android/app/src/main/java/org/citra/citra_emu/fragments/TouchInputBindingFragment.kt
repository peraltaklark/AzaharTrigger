// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
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

        val adapter = ArrayAdapter(
            requireContext(),
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
        binding.editProfileButton.setOnClickListener { showEditProfileDialog() }
        binding.deleteProfileButton.setOnClickListener { showDeleteProfileDialog() }
        binding.addProfileButton.setOnClickListener { showCreateProfileDialog() }
        binding.deleteAllButton.setOnClickListener { showDeleteAllBindingsDialog() }
    }

    // ----------------------------------------------------
    // Profile Dialogs
    // ----------------------------------------------------

    private fun showCreateProfileDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.profile_name)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.create_new_profile)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    return@setPositiveButton
                }

                if (profileManager.createProfile(name)) {
                    selectProfile(name)
                    updateProfileSpinner()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Profile already exists",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditProfileDialog() {
        val input = EditText(requireContext()).apply {
            setText(currentProfile)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Profile Name")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty() || newName == currentProfile) {
                    return@setPositiveButton
                }

                if (profileManager.renameProfile(currentProfile, newName)) {
                    currentProfile = newName
                    updateProfileSpinner()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Profile name already exists",
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
                "Cannot delete Default profile",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Profile")
            .setMessage("Are you sure you want to delete \"$currentProfile\"?")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (profileManager.deleteProfile(currentProfile)) {
                    selectProfile(DEFAULT_PROFILE)
                    updateProfileSpinner()
                    Toast.makeText(
                        requireContext(),
                        "Profile deleted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteAllBindingsDialog() {
        AlertDialog.Builder(requireContext())
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
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val xInput = EditText(requireContext()).apply {
            hint = getString(R.string.x_coordinate)
            setText(formatCoordinate(touchBinding.x))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val yInput = EditText(requireContext()).apply {
            hint = getString(R.string.y_coordinate)
            setText(formatCoordinate(touchBinding.y))
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        container.addView(xInput)
        container.addView(yInput)

        AlertDialog.Builder(requireContext())
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
                        "Invalid coordinates (0.0 - 1.0)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteBindingDialog(touchBinding: TouchInputBinding) {
        AlertDialog.Builder(requireContext())
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

    private fun formatCoordinate(value: Float): String {
        return String.format(MASK_COORDINATE_FORMAT, value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
