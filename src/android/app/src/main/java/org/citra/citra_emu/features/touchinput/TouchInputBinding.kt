// Copyright 2025 Azahar Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

data class TouchInputBinding(
    val keyCode: Int = -1,
    val axis: Int = -1,
    val positive: Boolean = true,
    val analog: Boolean = false,
    val threshold: Float = 0.5f,
    val x: Float,
    val y: Float
)