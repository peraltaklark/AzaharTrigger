// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A controller key or axis mapped to normalized (x, y) coordinates on the 3DS bottom screen.
 */
data class TouchInputBinding(
    val keyCode: Int = DEFAULT_UNBOUND,
    val axis: Int = DEFAULT_UNBOUND,
    val positive: Boolean = true,
    val analog: Boolean = false,
    val threshold: Float = DEFAULT_THRESHOLD,
    val x: Float = DEFAULT_COORDINATE,
    val y: Float = DEFAULT_COORDINATE
) {
    val isAxisBinding: Boolean
        get() = axis != DEFAULT_UNBOUND

    val isKeyBinding: Boolean
        get() = keyCode != DEFAULT_UNBOUND

    /** Whether this binding listens to the same physical input as [other]. */
    fun hasSameInputAs(other: TouchInputBinding): Boolean =
        keyCode == other.keyCode &&
            axis == other.axis &&
            positive == other.positive &&
            analog == other.analog

    /** Readable name of the bound input, e.g. "Volume Up" or "Left Stick X +". */
    fun displayName(): String =
        if (isAxisBinding) {
            val name = MotionEvent.axisToString(axis).toDisplayName(AXIS_PREFIX)
            "$name ${if (positive) "+" else "\u2212"}"
        } else {
            KeyEvent.keyCodeToString(keyCode).toDisplayName(KEYCODE_PREFIX)
        }

    /** Touch position as percentages, e.g. "X 90% · Y 90%". */
    fun positionLabel(): String = formatPosition(x, y)

    fun toJson(): JSONObject =
        JSONObject().apply {
            put(JSON_KEY_CODE, keyCode)
            put(JSON_AXIS, axis)
            put(JSON_POSITIVE, positive)
            put(JSON_ANALOG, analog)
            put(JSON_THRESHOLD, threshold)
            put(JSON_X, x)
            put(JSON_Y, y)
        }

    companion object {
        const val DEFAULT_UNBOUND = -1
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_COORDINATE = 0.5f

        private const val TAG = "TouchInputBinding"

        private const val KEYCODE_PREFIX = "KEYCODE_"
        private const val AXIS_PREFIX = "AXIS_"

        private const val JSON_KEY_CODE = "keyCode"
        private const val JSON_AXIS = "axis"
        private const val JSON_POSITIVE = "positive"
        private const val JSON_ANALOG = "analog"
        private const val JSON_THRESHOLD = "threshold"
        private const val JSON_X = "x"
        private const val JSON_Y = "y"

        fun formatPosition(x: Float, y: Float): String =
            "X ${(x * 100).roundToInt()}% \u00B7 Y ${(y * 100).roundToInt()}%"

        fun fromJson(json: JSONObject): TouchInputBinding =
            TouchInputBinding(
                keyCode = json.optInt(JSON_KEY_CODE, DEFAULT_UNBOUND),
                axis = json.optInt(JSON_AXIS, DEFAULT_UNBOUND),
                positive = json.optBoolean(JSON_POSITIVE, true),
                analog = json.optBoolean(JSON_ANALOG, false),
                threshold = json.optDouble(JSON_THRESHOLD, DEFAULT_THRESHOLD.toDouble()).toFloat(),
                x = json.optDouble(JSON_X, DEFAULT_COORDINATE.toDouble()).toFloat(),
                y = json.optDouble(JSON_Y, DEFAULT_COORDINATE.toDouble()).toFloat()
            )

        fun listToJson(bindings: List<TouchInputBinding>): String {
            val array = JSONArray()
            bindings.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        /** Parses a JSON array of bindings, returning an empty list if it is missing or invalid. */
        fun listFromJson(json: String?): List<TouchInputBinding> {
            if (json.isNullOrEmpty()) {
                return emptyList()
            }

            return try {
                val array = JSONArray(json)
                List(array.length()) { fromJson(array.getJSONObject(it)) }
            } catch (e: JSONException) {
                Log.e(TAG, "Failed to parse touch input bindings", e)
                emptyList()
            }
        }
    }
}

private fun String.toDisplayName(prefix: String): String =
    removePrefix(prefix)
        .split('_')
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
