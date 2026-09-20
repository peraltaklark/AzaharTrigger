// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

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
) : ListAdapter<TouchInputBinding, TouchInputBindingAdapter.ViewHolder>(DiffCallback) {

    private companion object {
        const val PAYLOAD_NUMBER = "number"
        const val MENU_EDIT = 1
        const val MENU_DELETE = 2
    }

    /**
     * Row numbers depend on list position, so unchanged rows that shift after an
     * insert or delete still need their badge refreshed. The payload keeps the
     * refresh silent (no change animation).
     */
    override fun submitList(list: List<TouchInputBinding>?) {
        super.submitList(list) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_NUMBER)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTouchInputBindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position + 1, getItem(position))
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_NUMBER)) {
            holder.bindNumber(position + 1)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ViewHolder(
        private val binding: ItemTouchInputBindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(number: Int, touchBinding: TouchInputBinding) {
            bindNumber(number)
            binding.bindingName.text = touchBinding.displayName()
            binding.bindingCoordinates.text = touchBinding.positionLabel()
            binding.menuButton.setOnClickListener { showMenu(touchBinding) }
        }

        fun bindNumber(number: Int) {
            binding.bindingNumber.text = number.toString()
        }

        private fun showMenu(touchBinding: TouchInputBinding) {
            PopupMenu(binding.root.context, binding.menuButton).apply {
                menu.add(0, MENU_EDIT, 0, R.string.edit)
                menu.add(0, MENU_DELETE, 1, R.string.delete)

                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        MENU_EDIT -> onEditClicked(touchBinding)
                        MENU_DELETE -> onDeleteClicked(touchBinding)
                        else -> return@setOnMenuItemClickListener false
                    }
                    true
                }
                show()
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TouchInputBinding>() {

        // A binding is identified by the physical input it listens to
        override fun areItemsTheSame(
            oldItem: TouchInputBinding,
            newItem: TouchInputBinding
        ): Boolean = oldItem.hasSameInputAs(newItem)

        override fun areContentsTheSame(
            oldItem: TouchInputBinding,
            newItem: TouchInputBinding
        ): Boolean = oldItem == newItem
    }
}
