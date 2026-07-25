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
    
    private val chatAdapter = ChatOverlayAdapter()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    
    // ==================== INIT ====================
    
    init {
        setupRecyclerView()
        setupChatButton()
        setupNetPlayListener()
        updateChatButtonVisibility()
        loadSettings()
    }
    
    // ==================== SETUP METHODS ====================
    
    /**
     * Initializes the RecyclerView with layout manager and adapter
     */
    private fun setupRecyclerView() {
        chatRecycler.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
        }
    }
    
    /**
     * Configures the chat button click listener and drag functionality
     */
    private fun setupChatButton() {
        chatButton.setOnClickListener {
            ChatDialog(context).show()
        }
        setupDraggableChatButton()
        chatButton.visibility = if (NetPlayManager.netPlayIsJoined()) View.VISIBLE else View.GONE
    }
    
    /**
     * Sets up the NetPlay message listener to receive chat messages
     */
    private fun setupNetPlayListener() {
        NetPlayManager.setOnMessageReceivedListener { type, msg ->
            (context as? android.app.Activity)?.runOnUiThread {
                if (!NetPlayManager.netPlayIsJoined()) {
                    clearAllMessages()
                    updateChatButtonVisibility()
                    return@runOnUiThread
                }
                displayNewMessage(type, msg)
                updateChatButtonVisibility()
            }
        }
    }
    
    // ==================== SETTINGS METHODS ====================
    
    /**
     * Loads saved settings from preferences
     */
    private fun loadSettings() {
        applyTextSize(IntSetting.CHAT_TEXT_SIZE.int.toFloat())
        applyBackgroundOpacity(IntSetting.CHAT_BACKGROUND_OPACITY.int)
        applyFabOpacity(IntSetting.CHAT_FAB_OPACITY.int)
        applyFabSize(IntSetting.CHAT_FAB_SIZE.int)
        applyShadowRadius(IntSetting.CHAT_SHADOW_RADIUS.int.toFloat())
    }
    
    /**
     * Applies text size to the RecyclerView
     */
    private fun applyTextSize(size: Float) {
        chatAdapter.setTextSize(size)
    }
    
    /**
     * Applies background opacity to the chat container
     */
    private fun applyBackgroundOpacity(opacity: Int) {
        val alpha = opacity / 100f
        val backgroundColor = Color.argb(
            (alpha * 255).toInt(),
            0,
            0,
            0
        )
        chatContainer.setBackgroundColor(backgroundColor)
    }
    
    /**
     * Applies opacity to the FAB button
     */
    private fun applyFabOpacity(opacity: Int) {
        val alpha = opacity / 100f
        chatButton.alpha = alpha
    }
    
    /**
     * Applies size to the FAB button
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
    }
    
    /**
     * Applies shadow radius to chat messages
     */
    private fun applyShadowRadius(radius: Float) {
        chatAdapter.setShadowRadius(radius)
    }
    
    // ==================== PUBLIC METHODS ====================
    
    /**
     * Displays a new chat message in the overlay
     */
    fun displayNewMessage(type: Int, msg: String) {
        val formattedMessage = formatMessageByType(type, msg)
        chatAdapter.add(formattedMessage)
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
     * Called when the fragment resumes - refreshes chat state
     */
    fun onFragmentResume() {
        updateChatButtonVisibility()
        loadSettings()
    }
    
    /**
     * Returns the chat adapter for external access if needed
     */
    fun getChatAdapter(): ChatOverlayAdapter = chatAdapter
    
    /**
     * Cleans up resources when the fragment is destroyed
     */
    fun cleanup() {
        NetPlayManager.setOnMessageReceivedListener(null)
        clearAllMessages()
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Formats the message with appropriate prefix based on message type
     */
    private fun formatMessageByType(type: Int, msg: String): String {
        return when (type) {
            NetPlayManager.NetPlayStatus.CHAT_MESSAGE -> msg
            NetPlayManager.NetPlayStatus.MEMBER_JOIN -> "➕ $msg"
            NetPlayManager.NetPlayStatus.MEMBER_LEAVE -> "➖ $msg"
            NetPlayManager.NetPlayStatus.MEMBER_KICKED -> "❌ $msg"
            NetPlayManager.NetPlayStatus.MEMBER_BANNED -> "🚫 $msg"
            else -> msg
        }
    }
    
    /**
     * Makes the chat button draggable within its parent bounds
     */
    private fun setupDraggableChatButton() {
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        chatButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = kotlin.math.abs(event.rawX - initialTouchX)
                    val deltaY = kotlin.math.abs(event.rawY - initialTouchY)
                    
                    if (deltaX > 8 || deltaY > 8) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val parent = view.parent as View
                        val newX = (event.rawX + view.x - initialTouchX)
                            .coerceIn(0f, (parent.width - view.width).toFloat())
                        val newY = (event.rawY + view.y - initialTouchY)
                            .coerceIn(0f, (parent.height - view.height).toFloat())
                        
                        view.x = newX
                        view.y = newY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
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
        chatRecycler.scrollToPosition(chatAdapter.itemCount - 1)
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
    
    // ==================== INNER ADAPTER CLASS ====================
    
    /**
     * Inner adapter class for chat messages
     */
    inner class ChatOverlayAdapter : RecyclerView.Adapter<ChatOverlayAdapter.ChatViewHolder>() {
        
        private val messages = mutableListOf<String>()
        private var textSize = 14f
        private var shadowRadius = 2f

        class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(R.id.chat_overlay_message_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_chat_overlay, parent, false)
            return ChatViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
            holder.textView.text = messages[position]
            holder.textView.textSize = textSize
            holder.textView.setShadowLayer(shadowRadius, 1f, 1f, Color.BLACK)
        }

        override fun getItemCount(): Int = messages.size

        fun add(message: String) {
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }

        fun clear() {
            messages.clear()
            notifyDataSetChanged()
        }

        fun setTextSize(size: Float) {
            textSize = size
            notifyDataSetChanged()
        }

        fun setShadowRadius(radius: Float) {
            shadowRadius = radius
            notifyDataSetChanged()
        }
    }
}