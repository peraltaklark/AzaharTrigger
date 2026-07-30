// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

/**
 * Represents a physical controller key or axis binding mapped to a specific set of
 * normalized screen coordinates (x, y) on the emulator's bottom display.
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
    /**
     * Helper check to determine if this binding originates from an analog axis input.
     */
    val isAxisBinding: Boolean
        get() = axis != DEFAULT_UNBOUND

    /**
     * Helper check to determine if this binding originates from a physical key event.
     */
    val isKeyBinding: Boolean
        get() = keyCode != DEFAULT_UNBOUND

    companion object {
        const val DEFAULT_UNBOUND = -1
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_COORDINATE = 0.5f
    }
}
