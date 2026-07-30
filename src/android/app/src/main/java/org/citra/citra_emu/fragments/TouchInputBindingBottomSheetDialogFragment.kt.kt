// Copyright 2025 Azahar Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.DialogInputBinding
import org.citra.citra_emu.features.touchinput.TouchInputBinding
import org.citra.citra_emu.features.touchinput.TouchInputBindingManager
import kotlin.math.abs

class TouchInputBindingBottomSheetDialogFragment :
    BottomSheetDialogFragment() {

    companion object {
        private const val ARG_X = "touch_x"
        private const val ARG_Y = "touch_y"

        fun newInstance(
            x: Float,
            y: Float
        ): TouchInputBindingBottomSheetDialogFragment {
            return TouchInputBindingBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putFloat(ARG_X, x)
                    putFloat(ARG_Y, y)
                }
            }
        }
    }

    private var _binding: DialogInputBinding? = null

    private val binding: DialogInputBinding
        get() = _binding!!

    private var touchX = 0f
    private var touchY = 0f
    private var bindingAdded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        touchX = arguments?.getFloat(ARG_X) ?: 0f
        touchY = arguments?.getFloat(ARG_Y) ?: 0f
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogInputBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val parent = view.parent as? View

        parent?.let {
            BottomSheetBehavior.from(it).state =
                BottomSheetBehavior.STATE_EXPANDED
        }

        isCancelable = true

        binding.textTitle.text =
            getString(R.string.bind_touch_input)

        binding.textMessage.text =
            getString(R.string.bind_touch_input_message)

        binding.buttonCancel.text =
            getString(R.string.bind_touch_input_cancel)

        (binding.buttonClear.parent as? ViewGroup)
            ?.removeView(binding.buttonClear)

        dialog?.setOnKeyListener { _, _, event ->
            handleKeyEvent(event)
        }

        dialog?.window?.decorView?.setOnGenericMotionListener { _, event ->
            handleAxisEvent(event)
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.device == null) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false

        TouchInputBindingManager.addBinding(
            TouchInputBinding(
                keyCode = event.keyCode,
                axis = -1,
                positive = true,
                analog = false,
                threshold = 0.5f,
                x = touchX,
                y = touchY
            )
        )

        bindingAdded = true
        notifyBindingAdded()
        dismiss()

        return true
    }

    private fun handleAxisEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) {
            return false
        }

        if (event.source and InputDevice.SOURCE_CLASS_JOYSTICK == 0) {
            return false
        }

        val device = event.device ?: return false

        for (range in device.motionRanges) {
            val value = event.getAxisValue(range.axis)

            if (abs(value) < 0.7f) {
                continue
            }

            TouchInputBindingManager.addBinding(
                TouchInputBinding(
                    keyCode = -1,
                    axis = range.axis,
                    positive = value > 0f,
                    analog = false,
                    threshold = 0.7f,
                    x = touchX,
                    y = touchY
                )
            )

            bindingAdded = true
            notifyBindingAdded()
            dismiss()

            return true
        }

        return false
    }

    private fun notifyBindingAdded() {
        parentFragmentManager.setFragmentResult(
            "touch_binding_added",
            Bundle()
        )
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!bindingAdded) {
            parentFragmentManager.setFragmentResult(
                "touch_binding_cancelled",
                Bundle()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}