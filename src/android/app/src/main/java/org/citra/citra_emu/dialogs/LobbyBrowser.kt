// Copyright 2025 Azahar Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included

package org.citra.citra_emu.dialogs

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.citra.citra_emu.R
import org.citra.citra_emu.databinding.DialogLobbyBrowserBinding
import org.citra.citra_emu.databinding.ItemLobbyRoomBinding
import org.citra.citra_emu.utils.NetPlayManager
import java.util.Locale

class LobbyBrowser(context: Context) : BottomSheetDialog(context) {
    private lateinit var binding: DialogLobbyBrowserBinding
    private val activity: Activity? = context as? Activity
        ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
    private lateinit var adapter: LobbyRoomAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { adapter.filterAndSearch() }

    private val preferences: SharedPreferences =
        context.getSharedPreferences("lobby_history", Context.MODE_PRIVATE)

    // Cached preference variables to eliminate disk reads on UI filter passes
    private var lastIp: String? = null
    private var lastPort: Int = -1
    private var lastName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        binding = DialogLobbyBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load saved username from NetPlayManager
        binding.usernameInput.setText(NetPlayManager.getUsername(context))

        // Save username automatically as user types
        binding.usernameInput.doOnTextChanged { text, _, _, _ ->
            activity?.let {
                NetPlayManager.setUsername(it, text.toString())
            }
        }

        // Cache last visited room details in memory
        lastIp = preferences.getString("last_room_ip", null)
        lastPort = preferences.getInt("last_room_port", -1)
        lastName = preferences.getString("last_room_name", "")

        binding.emptyRefreshButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            refreshRoomList()
        }

        setupRecyclerView()
        setupRefreshButton()
        setupSearchBar()

        // Show local cache instantly while network refresh runs
        refreshRoomList()

        setOnDismissListener {
            activity?.let {
                NetPlayManager.setUsername(it, binding.usernameInput.text.toString())
            }
            NetPlayDialog(context).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = LobbyRoomAdapter { room -> handleRoomSelection(room) }

        binding.roomList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@LobbyBrowser.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupRefreshButton() {
        binding.refreshButton.setOnClickListener {
            binding.refreshButton.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE
            refreshRoomList()
        }
    }

    private fun setupSearchBar() {
        binding.chipHideEmpty.setOnClickListener { adapter.filterAndSearch() }
        binding.chipHideFull.setOnClickListener { adapter.filterAndSearch() }
        binding.chipHideLocked.setOnClickListener { adapter.filterAndSearch() }

        binding.searchText.doOnTextChanged { text: CharSequence?, _: Int, _: Int, _: Int ->
            binding.clearButton.visibility =
                if (text.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
            // Debounce: wait 300ms after user stops typing before filtering
            handler.removeCallbacks(searchRunnable)
            handler.postDelayed(searchRunnable, 300)
        }

        binding.clearButton.setOnClickListener {
            binding.searchText.setText("")
            handler.removeCallbacks(searchRunnable)
            adapter.filterAndSearch()
        }
    }

    private fun refreshRoomList() {
        // 1. Instantly display whatever is already in local memory
        val cachedRooms = NetPlayManager.getPublicRooms()
        if (cachedRooms.isNotEmpty()) {
            adapter.updateRooms(moveLastVisitedRoomToTop(cachedRooms))
            binding.emptyView.visibility = View.GONE
            binding.roomList.visibility = View.VISIBLE
        }

        // 2. Refresh from network asynchronously
        NetPlayManager.refreshRoomListAsync { rooms ->
            binding.emptyView.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
            binding.roomList.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE
            binding.appbar.visibility = if (rooms.isEmpty()) View.GONE else View.VISIBLE

            adapter.filterAndSearch(rooms)
            binding.refreshButton.isEnabled = true
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun handleRoomSelection(room: NetPlayManager.RoomInfo) {
        if (room.hasPassword) {
            showPasswordDialog(room)
        } else {
            joinRoom(room, "")
        }
    }

    private fun showPasswordDialog(room: NetPlayManager.RoomInfo) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_password_input, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.password_input)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.multiplayer_password_required))
            .setView(dialogView)
            .setPositiveButton(R.string.multiplayer_join_room) { _, _ ->
                joinRoom(room, passwordInput.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveLastVisitedRoom(room: NetPlayManager.RoomInfo) {
        lastIp = room.ip
        lastPort = room.port
        lastName = room.name

        preferences.edit()
            .putString("last_room_ip", room.ip)
            .putInt("last_room_port", room.port)
            .putString("last_room_name", room.name)
            .apply()
    }

    private fun moveLastVisitedRoomToTop(
        rooms: List<NetPlayManager.RoomInfo>
    ): List<NetPlayManager.RoomInfo> {
        val ip = lastIp
        val port = lastPort

        if (ip == null || port == -1) {
            return rooms
        }

        val name = lastName
        val lastRoom = rooms.find {
            it.ip == ip && it.port == port && (name.isNullOrEmpty() || it.name == name)
        }

        return if (lastRoom != null) {
            listOf(lastRoom) + rooms.filter { it != lastRoom }
        } else {
            rooms
        }
    }

    private fun joinRoom(room: NetPlayManager.RoomInfo, password: String) {
        val username = binding.usernameInput.text.toString().ifEmpty {
            NetPlayManager.getUsername(context)
        }

        Thread {
            val result = NetPlayManager.netPlayJoinRoom(room.ip, room.port, username, password)

            handler.post {
                if (result == 0) {
                    saveLastVisitedRoom(room)
                    dismiss()
                }
            }
        }.start()
    }

    inner class LobbyRoomAdapter(private val onRoomSelected: (NetPlayManager.RoomInfo) -> Unit) :
        RecyclerView.Adapter<LobbyRoomAdapter.RoomViewHolder>() {

        private val rooms = mutableListOf<NetPlayManager.RoomInfo>()
        private var searchJob: Job? = null

        inner class RoomViewHolder(private val binding: ItemLobbyRoomBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(room: NetPlayManager.RoomInfo) {
                binding.roomName.text = room.name
                // Player count with icon (icon is in the layout)
                binding.playerCount.text = "${room.members.size}/${room.maxPlayers}"

                binding.lockIcon.visibility = if (room.hasPassword) View.VISIBLE else View.GONE

                if (room.preferredGameName.isNotEmpty() && room.preferredGameId != 0L) {
                    binding.gameName.text = room.preferredGameName
                } else {
                    binding.gameName.text = context.getString(R.string.multiplayer_no_game_info)
                }

                // Show host only if it exists and is not empty
                if (room.owner.isNotEmpty()) {
                    binding.roomHost.text = "Host: ${room.owner}"
                    binding.roomHost.visibility = View.VISIBLE
                } else {
                    binding.roomHost.visibility = View.GONE
                }

                // Populate player chips
                binding.playerChipGroup.removeAllViews()
                
                if (room.members.isNotEmpty()) {
                    for (member in room.members) {
                        val chip = com.google.android.material.chip.Chip(context).apply { text = if (member.username.isNotEmpty()) member.username else member.nickname; isClickable = false; isCheckable = false; textSize = 10f; chipMinHeight = 20f; chipCornerRadius = 4f; setChipBackgroundColorResource(android.R.color.darker_gray); chipStrokeWidth = 1f; setChipStrokeColorResource(android.R.color.darker_gray) }
                        binding.playerChipGroup.addView(chip)
                    }
                }

                itemView.setOnClickListener { onRoomSelected(room) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
            val binding = ItemLobbyRoomBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return RoomViewHolder(binding)
        }

        override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
            holder.bind(rooms[position])
        }

        override fun getItemCount() = rooms.size

        /**
         * Updates rooms smoothly using DiffUtil. Detects player count,
         * password status, and room name changes without re-rendering the whole list.
         */
        fun updateRooms(newRooms: List<NetPlayManager.RoomInfo>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = rooms.size
                override fun getNewListSize() = newRooms.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = rooms[oldItemPosition]
                    val new = newRooms[newItemPosition]
                    return old.ip == new.ip && old.port == new.port
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = rooms[oldItemPosition]
                    val new = newRooms[newItemPosition]
                    return old.members.size == new.members.size &&
                           old.maxPlayers == new.maxPlayers &&
                           old.name == new.name &&
                           old.hasPassword == new.hasPassword &&
                           old.preferredGameName == new.preferredGameName
                }
            })

            rooms.clear()
            rooms.addAll(newRooms)
            diffResult.dispatchUpdatesTo(this)
        }

        /**
         * Offloads filter and search execution to Dispatchers.Default.
         * Cancels previous search jobs on new text keystrokes to preserve UI response time.
         */
        fun filterAndSearch(
            sourceRooms: List<NetPlayManager.RoomInfo> = NetPlayManager.getPublicRooms()
        ) {
            searchJob?.cancel()

            val query = binding.searchText.text.toString().trim().lowercase(Locale.getDefault())
            val hideEmpty = binding.chipHideEmpty.isChecked
            val hideFull = binding.chipHideFull.isChecked
            val hideLocked = binding.chipHideLocked.isChecked

            searchJob = CoroutineScope(Dispatchers.Default).launch {
                var filteredList = sourceRooms

                if (hideEmpty) {
                    filteredList = filteredList.filter { it.members.isNotEmpty() }
                }
                if (hideFull) {
                    filteredList = filteredList.filter { it.members.size < it.maxPlayers }
                }
                if (hideLocked) {
                    filteredList = filteredList.filter { !it.hasPassword }
                }

                if (query.isNotEmpty()) {
                    filteredList = filteredList.filter { room ->
                        room.name.lowercase(Locale.getDefault()).contains(query) ||
                        room.owner.lowercase(Locale.getDefault()).contains(query) ||
                        room.preferredGameName.lowercase(Locale.getDefault()).contains(query) ||
                        room.members.any { member ->
                            member.nickname.lowercase(Locale.getDefault()).contains(query)
                        }
                    }
                }

                val finalList = moveLastVisitedRoomToTop(filteredList)

                withContext(Dispatchers.Main) {
                    updateRooms(finalList)
                }
            }
        }
    }
}
