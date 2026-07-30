// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.utils.ControllerMappingHelper
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

object TouchInputBindingManager {

    private const val TAG = "TouchInputBinding"
    private const val PREF_KEY = "TouchscreenBindings"
    private const val AXIS_DEADZONE = 0.15f
    private const val TOUCH_PROXIMITY_THRESHOLD = 0.02f
    private const val FRAMEBUFFER_MIN_SIZE = 6

    // JSON Serialization Keys
    private const val JSON_KEY_CODE = "keyCode"
    private const val JSON_AXIS = "axis"
    private const val JSON_POSITIVE = "positive"
    private const val JSON_ANALOG = "analog"
    private const val JSON_THRESHOLD = "threshold"
    private const val JSON_X = "x"
    private const val JSON_Y = "y"

    private val preferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(
            CitraApplication.appContext
        )

    private val bindings = mutableListOf<TouchInputBinding>()
    private val axisStates = mutableMapOf<String, Boolean>()
    private val keyStates = mutableSetOf<Int>()

    init {
        loadBindings()
    }

    // --- State Accessors ---

    fun getBindings(): List<TouchInputBinding> = bindings.toList()

    fun addBinding(binding: TouchInputBinding) {
        bindings.removeAll {
            it.keyCode == binding.keyCode &&
                it.axis == binding.axis &&
                it.positive == binding.positive &&
                it.analog == binding.analog
        }

        bindings.add(binding)
        saveBindings()
    }

    fun removeBinding(binding: TouchInputBinding) {
        bindings.remove(binding)
        saveBindings()
    }

    fun clearBindings() {
        bindings.clear()
        axisStates.clear()
        keyStates.clear()

        preferences.edit()
            .remove(PREF_KEY)
            .apply()
    }

    fun setBindings(newBindings: List<TouchInputBinding>) {
        bindings.clear()
        bindings.addAll(newBindings)
        axisStates.clear()
        keyStates.clear()
    }

    // --- Input Event Dispatching ---

    fun sendTouch(binding: TouchInputBinding, pressed: Boolean) {
        val layout = NativeLibrary.getFramebufferLayout()

        if (layout.size < FRAMEBUFFER_MIN_SIZE) {
            Log.w(TAG, "sendTouch: framebuffer layout is too small")
            return
        }

        val rectLeft = layout[2].toFloat()
        val rectTop = layout[3].toFloat()
        val rectRight = layout[4].toFloat()
        val rectBottom = layout[5].toFloat()

        val x = rectLeft + binding.x * (rectRight - rectLeft)
        val y = rectTop + binding.y * (rectBottom - rectTop)

        NativeLibrary.onTouchEvent(x, y, pressed)
    }

    fun handleKeyDown(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (ControllerMappingHelper.shouldKeyBeIgnored(event.device, event.keyCode)) return false
        if (!keyStates.add(event.keyCode)) return false // Returns false if key was already present

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis == -1 && binding.keyCode == event.keyCode) {
                sendTouch(binding, true)
                handled = true
            }
        }

        if (!handled) {
            keyStates.remove(event.keyCode)
        }

        return handled
    }

    fun handleKeyUp(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        if (ControllerMappingHelper.shouldKeyBeIgnored(event.device, event.keyCode)) return false
        if (!keyStates.remove(event.keyCode)) return false // Returns false if key was not tracked

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis == -1 && binding.keyCode == event.keyCode) {
                sendTouch(binding, false)
                handled = true
            }
        }

        return handled
    }

    fun handleAxisMotion(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false

        var handled = false

        bindings.forEach { binding ->
            if (binding.axis < 0) return@forEach

            val rawAxisValue = event.getAxisValue(binding.axis)
            val value = ControllerMappingHelper.scaleAxis(
                event.device,
                binding.axis,
                rawAxisValue
            )

            val pressed = if (binding.analog) {
                abs(value) > binding.threshold
            } else if (binding.positive) {
                value > AXIS_DEADZONE
            } else {
                value < -AXIS_DEADZONE
            }

            val stateKey = "${binding.axis}_${binding.positive}_${binding.analog}"
            val oldState = axisStates[stateKey] ?: false

            if (oldState != pressed) {
                sendTouch(binding, pressed)
                axisStates[stateKey] = pressed
                handled = true
            }
        }

        return handled
    }

    fun getBindingAt(x: Float, y: Float): TouchInputBinding? {
        return bindings.firstOrNull {
            abs(it.x - x) < TOUCH_PROXIMITY_THRESHOLD &&
                abs(it.y - y) < TOUCH_PROXIMITY_THRESHOLD
        }
    }

    // --- Persistence ---

    private fun saveBindings() {
        val array = JSONArray()

        bindings.forEach { binding ->
            val obj = JSONObject().apply {
                put(JSON_KEY_CODE, binding.keyCode)
                put(JSON_AXIS, binding.axis)
                put(JSON_POSITIVE, binding.positive)
                put(JSON_ANALOG, binding.analog)
                put(JSON_THRESHOLD, binding.threshold)
                put(JSON_X, binding.x)
                put(JSON_Y, binding.y)
            }
            array.put(obj)
        }

        preferences.edit()
            .putString(PREF_KEY, array.toString())
            .apply()
    }

    private fun loadBindings() {
        bindings.clear()

        try {
            val json = preferences.getString(PREF_KEY, "[]") ?: "[]"
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)

                bindings.add(
                    TouchInputBinding(
                        keyCode = obj.optInt(JSON_KEY_CODE, -1),
                        axis = obj.optInt(JSON_AXIS, -1),
                        positive = obj.optBoolean(JSON_POSITIVE, true),
                        analog = obj.optBoolean(JSON_ANALOG, false),
                        threshold = obj.optDouble(JSON_THRESHOLD, 0.5).toFloat(),
                        x = obj.optDouble(JSON_X, 0.5).toFloat(),
                        y = obj.optDouble(JSON_Y, 0.5).toFloat()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed loading touch input bindings", e)
            bindings.clear()
        }
    }
}
