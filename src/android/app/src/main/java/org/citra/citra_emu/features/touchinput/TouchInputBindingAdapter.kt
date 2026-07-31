// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.ItemTouchInputBindingBinding

class TouchInputBindingAdapter(
    private val onEditClicked: (TouchInputBinding) -> Unit,
    private val onDeleteClicked: (TouchInputBinding) -> Unit
) : ListAdapter<TouchInputBinding, TouchInputBindingAdapter.ViewHolder>(
    TouchInputBindingDiffCallback()
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemTouchInputBindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(
            position + 1,
            getItem(position)
        )
    }

    inner class ViewHolder(
        private val binding: ItemTouchInputBindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            number: Int,
            touchBinding: TouchInputBinding
        ) {
            binding.bindingNumber.text = number.toString()
            binding.bindingName.text = getBindingName(touchBinding)

            binding.bindingCoordinates.text = binding.root.context.getString(
                R.string.touch_input_coordinates,
                String.format("%.3f", touchBinding.x),
                String.format("%.3f", touchBinding.y)
            )

            binding.menuButton.setOnClickListener {
                PopupMenu(binding.root.context, binding.menuButton).apply {
                    menu.add(0, 1, 0, R.string.edit)
                    menu.add(0, 2, 1, R.string.delete)

                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            1 -> {
                                onEditClicked(touchBinding)
                                true
                            }
                            2 -> {
                                onDeleteClicked(touchBinding)
                                true
                            }
                            else -> false
                        }
                    }
                    show()
                }
            }
        }
    }

    private fun getBindingName(touchBinding: TouchInputBinding): String {
        return if (touchBinding.axis >= 0) {
            "Axis ${touchBinding.axis} ${if (touchBinding.positive) "+" else "-"}"
        } else {
            KeyEvent.keyCodeToString(touchBinding.keyCode)
        }
    }
}

class TouchInputBindingDiffCallback : DiffUtil.ItemCallback<TouchInputBinding>() {

    override fun areItemsTheSame(
        oldItem: TouchInputBinding,
        newItem: TouchInputBinding
    ): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(
        oldItem: TouchInputBinding,
        newItem: TouchInputBinding
    ): Boolean {
        return oldItem == newItem
    }
}
