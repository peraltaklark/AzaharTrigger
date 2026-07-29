// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.overlay

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.citra.citra_emu.R
import org.citra.citra_emu.dialogs.ChatDialog
import org.citra.citra_emu.features.settings.model.IntSetting
import org.citra.citra_emu.utils.NetPlayManager

/**
 * Manages the chat overlay UI for netplay sessions.
 * Handles displaying messages, auto-hiding, button positioning, and drag functionality.
 */
class ChatOverlayManager(
    private val chatContainer: View,
    private val chatRecycler: RecyclerView,
    private val chatButton: FloatingActionButton,
    private val context: Context
) {
    
    // ==================== PROPERTIES ====================
    
    /** Adapter for displaying chat messages */
    private val chatAdapter = ChatOverlayAdapter()
    
    /** Handler for main thread operations */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /** Runnable for auto-hiding chat container */
    private var autoHideRunnable: Runnable? = null
    
    /** Listener for netplay messages */
    private val messageListener: (Int, String) -> Unit = { type, msg ->
        (context as? android.app.Activity)?.runOnUiThread {

            if (type == NetPlayManager.NetPlayStatus.ROOM_IDLE) {
                clearAllMessages()
                chatButton.visibility = View.GONE
                return@runOnUiThread
            }

            if (!NetPlayManager.netPlayIsJoined()) {
                clearAllMessages()
                chatButton.visibility = View.GONE
                return@runOnUiThread
            }

            displayNewMessage(type, msg)
            updateChatButtonVisibility()
        }
    }

    /**
     * Maximum number of chat lines/messages kept visible in the overlay.
     * Older messages are removed when this limit is exceeded.
     */
    private var maxChatLines = 8
    
    /** Stores chat button position for restoration after app restart */
    private val chatButtonPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** Keys for storing FAB position as percentages */
    private val chatButtonXKey = "chat_button_position_x"
    private val chatButtonYKey = "chat_button_position_y"

    // ==================== INIT ====================
    
    init {
        setupRecyclerView()
        setupChatButton()
        setupNetPlayListener()
        loadSettings()
        updateChatButtonVisibility()
    }
    
    // ==================== SETUP METHODS ====================
    
    /**
     * Initializes the RecyclerView with layout manager and adapter
     */
    private fun setupRecyclerView() {
        chatRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
            itemAnimator = null
        }
    }
    
    /**
     * Configures the chat button click listener, drag functionality,
     * and restores its previous position.
     */
    private fun setupChatButton() {
        chatButton.setOnClickListener { ChatDialog(context).show() }
        chatButton.setPadding(0, 0, 0, 0)
        chatButton.scaleType = android.widget.ImageView.ScaleType.CENTER

        setupDraggableChatButton()

        chatButton.visibility = if (NetPlayManager.netPlayIsJoined()) View.VISIBLE else View.GONE
    }
    
    /**
     * Sets up the NetPlay message listener to receive chat messages
     */
    private fun setupNetPlayListener() {
        NetPlayManager.addOnMessageReceivedListener(messageListener)
    }
    
    // ==================== SETTINGS METHODS ====================
    
    /**
     * Loads saved settings from preferences and applies them
     */
    fun loadSettings() {
        applyTextSize(IntSetting.CHAT_TEXT_SIZE.int.toFloat())
        applyBackgroundOpacity(IntSetting.CHAT_BACKGROUND_OPACITY.int)
        applyFabOpacity(IntSetting.CHAT_FAB_OPACITY.int)
        applyFabSize(IntSetting.CHAT_FAB_SIZE.int)
        applyShadow(IntSetting.CHAT_SHADOW_RADIUS.int.toFloat(), IntSetting.CHAT_SHADOW_DX.int.toFloat(), IntSetting.CHAT_SHADOW_DY.int.toFloat())
        maxChatLines = IntSetting.CHAT_MAX_LINES.int
        chatButton.post {
            restoreChatButtonPosition()
    }
    
    /**
     * Applies text size to chat messages
     * @param size Text size in SP
     */
    private fun applyTextSize(size: Float) {
        chatAdapter.setTextSize(size)
    }
    
    /**
     * Applies background opacity to the chat container
     * @param opacity Opacity percentage (0-100)
     */
    private fun applyBackgroundOpacity(opacity: Int) {
        val alpha = opacity / 100f
        val backgroundColor = Color.argb((alpha * 255).toInt(), 0, 0, 0)
        chatContainer.setBackgroundColor(backgroundColor)
    }
    
    /**
     * Applies opacity to the FAB button
     * @param opacity Opacity percentage (0-100)
     */
    private fun applyFabOpacity(opacity: Int) {
        chatButton.alpha = opacity / 100f
    }
    
    /**
     * Applies size to the FAB button
     * @param sizeDp Size in DP
     */
    private fun applyFabSize(sizeDp: Int) {
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
        val layoutParams = chatButton.layoutParams
        layoutParams.width = sizePx
        layoutParams.height = sizePx
        chatButton.layoutParams = layoutParams
        chatButton.requestLayout()
        chatButton.setPadding(0, 0, 0, 0)
    }
    
    /**
     * Applies shadow effect to chat messages
     * @param radius Shadow radius in pixels
     * @param dx Shadow horizontal offset in pixels (positive = right, negative = left)
     * @param dy Shadow vertical offset in pixels (positive = down, negative = up)
     */
    private fun applyShadow(radius: Float, dx: Float, dy: Float) {
        chatAdapter.setShadow(radius, dx, dy)
    }
    
    // ==================== PUBLIC METHODS ====================
    
    /**
     * Displays a new chat message in the overlay
     * @param type Message type from NetPlayManager.NetPlayStatus
     * @param msg The message content
     */
    fun displayNewMessage(type: Int, msg: String) {
        val formattedMessage = formatMessageByType(type, msg).trim()

        // Ignore empty messages
        if (formattedMessage.isEmpty()) {
            return
        }

        chatAdapter.add(formattedMessage)

        // Keep only the latest configured number of messages
        while (chatAdapter.itemCount > maxChatLines) {
            chatAdapter.removeFirst()
        }

        showChatContainer()
        scrollToLatestMessage()
        scheduleAutoHide()
    }
    
    /**
     * Clears all chat messages and hides the overlay
     */
    fun clearAllMessages() {
        mainHandler.removeCallbacksAndMessages(null)
        autoHideRunnable?.let { mainHandler.removeCallbacks(it) }
        chatAdapter.clear()
        chatContainer.clearAnimation()
        chatContainer.animate().cancel()
        chatContainer.alpha = 1f
        chatContainer.visibility = View.GONE
    }
    
    /**
     * Updates the chat button visibility based on netplay status
     */
    fun updateChatButtonVisibility() {
        val isNetPlayActive = NetPlayManager.netPlayIsJoined()
        chatButton.visibility = if (isNetPlayActive) View.VISIBLE else View.GONE
        if (!isNetPlayActive) {
            clearAllMessages()
        }
    }
    
    /**
     * Called when the fragment resumes - refreshes chat state and settings
     */
    fun onFragmentResume() {
        updateChatButtonVisibility()
        loadSettings()
    }
    
    /**
     * Returns the chat adapter for external access if needed
     * @return The ChatOverlayAdapter instance
     */
    fun getChatAdapter(): ChatOverlayAdapter = chatAdapter
    
    /**
     * Cleans up resources when the fragment is destroyed
     */
    fun cleanup() {
        NetPlayManager.removeOnMessageReceivedListener(messageListener)
        clearAllMessages()
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Formats the message with appropriate prefix based on message type
     * @param type Message type from NetPlayManager.NetPlayStatus
     * @param msg The original message
     * @return Formatted message string
     */
    private fun formatMessageByType(type: Int, msg: String): String {
        return when (type) {
            NetPlayManager.NetPlayStatus.CHAT_MESSAGE -> msg.trim()
            NetPlayManager.NetPlayStatus.MEMBER_JOIN -> "➕ ${msg.trim()}"
            NetPlayManager.NetPlayStatus.MEMBER_LEAVE -> "➖ ${msg.trim()}"
            NetPlayManager.NetPlayStatus.MEMBER_KICKED -> "❌ ${msg.trim()}"
            NetPlayManager.NetPlayStatus.MEMBER_BANNED -> "🚫 ${msg.trim()}"
            else -> msg.trim()
        }
    }
    
    /** Saves button position as percentages of parent size */
    private fun saveChatButtonPosition() {
        val parent = chatButton.parent as? View ?: return
        val maxX = parent.width - chatButton.width
        val maxY = parent.height - chatButton.height
        if (maxX <= 0 || maxY <= 0) return

        val xPercent = (chatButton.x / maxX).coerceIn(0f, 1f)
        val yPercent = (chatButton.y / maxY).coerceIn(0f, 1f)

        chatButtonPreferences.edit()
            .putFloat(chatButtonXKey, xPercent)
            .putFloat(chatButtonYKey, yPercent)
            .apply()
    }

    /** Restores button position from saved percentages */
    private fun restoreChatButtonPosition() {
        val xPercent = chatButtonPreferences.getFloat(chatButtonXKey, -1f)
        val yPercent = chatButtonPreferences.getFloat(chatButtonYKey, -1f)
        if (xPercent < 0 || yPercent < 0) return

        val parent = chatButton.parent as? View ?: return
        parent.post {
            val maxX = parent.width - chatButton.width
            val maxY = parent.height - chatButton.height
            if (maxX > 0 && maxY > 0) {
                chatButton.x = maxX * xPercent
                chatButton.y = maxY * yPercent
            }
        }
    }
    
    /**
     * Makes the chat button draggable within its parent bounds
     */
    private fun setupDraggableChatButton() {
        var dX = 0f
        var dY = 0f
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false

        chatButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dX = view.x - downRawX
                    dY = view.y - downRawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - downRawX) > 8 ||
                        kotlin.math.abs(event.rawY - downRawY) > 8) {
                        dragging = true
                    }
                    if (dragging) {
                        val parent = view.parent as View
                        view.x = (event.rawX + dX)
                            .coerceIn(0f, (parent.width - view.width).toFloat())
                        view.y = (event.rawY + dY)
                            .coerceIn(0f, (parent.height - view.height).toFloat())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        saveChatButtonPosition()
                    } else {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Shows the chat container with full opacity
     */
    private fun showChatContainer() {
        chatContainer.visibility = View.VISIBLE
        chatContainer.alpha = 1f
    }
    
    /**
     * Scrolls the RecyclerView to show the latest message
     */
    private fun scrollToLatestMessage() {
        val itemCount = chatAdapter.itemCount
        if (itemCount > 0) {
            chatRecycler.scrollToPosition(itemCount - 1)
        }
    }
    
    /**
     * Schedules the chat container to auto-hide after 10 seconds
     */
    private fun scheduleAutoHide() {
        mainHandler.removeCallbacksAndMessages(null)
        autoHideRunnable = Runnable {
            chatContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    chatContainer.visibility = View.GONE
                    chatContainer.alpha = 1f
                }
        }
        mainHandler.postDelayed(autoHideRunnable!!, 10000)
    }
}

/**
 * Adapter for displaying chat messages in the overlay RecyclerView.
 * Handles message storage, text formatting, and view binding.
 */
class ChatOverlayAdapter : RecyclerView.Adapter<ChatOverlayAdapter.ChatViewHolder>() {
    
    /** List of chat messages */
    private val messages = mutableListOf<String>()
    
    /** Current text size in SP */
    private var textSize = 14f
    
    /** Current shadow radius in pixels */
    private var shadowRadius = 2f
    
    /** Shadow horizontal offset in pixels (positive = right, negative = left) */
    private var shadowDx = 1f
    
    /** Shadow vertical offset in pixels (positive = down, negative = up) */
    private var shadowDy = 1f

    /**
     * ViewHolder for chat message items
     */
    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.chat_overlay_message_text)
    }

    /**
     * Creates a new ViewHolder for chat messages
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_overlay, parent, false)
        return ChatViewHolder(view)
    }

    /**
     * Binds message data to the ViewHolder
     */
    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.textView.text = messages[position]
        holder.textView.textSize = textSize
        holder.textView.setShadowLayer(shadowRadius, shadowDx, shadowDy, Color.BLACK)
    }

    /**
     * Returns the total number of messages
     */
    override fun getItemCount(): Int = messages.size

    /**
     * Adds a new message to the list
     * @param message The message to add
     */
    fun add(message: String) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun removeFirst() {
        if (messages.isNotEmpty()) {
            messages.removeAt(0)
            notifyItemRemoved(0)
        }
    }

    /**
     * Clears all messages from the list
     */
    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    /**
     * Sets the text size for all messages
     * @param size Text size in SP
     */
    fun setTextSize(size: Float) {
        textSize = size
        notifyDataSetChanged()
    }

    /**
     * Sets the shadow effect for all messages
     * @param radius Shadow radius in pixels
     * @param dx Shadow horizontal offset in pixels (positive = right, negative = left)
     * @param dy Shadow vertical offset in pixels (positive = down, negative = up)
     */
    fun setShadow(radius: Float, dx: Float, dy: Float) {
        shadowRadius = radius
        shadowDx = dx
        shadowDy = dy
        notifyDataSetChanged()
    }
}
