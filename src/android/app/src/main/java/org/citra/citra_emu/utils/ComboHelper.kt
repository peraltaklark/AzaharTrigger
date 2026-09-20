// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.utils

import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.features.settings.model.IntListSetting

object ComboHelper {
    const val COMBO_COUNT = 5

    fun comboActivate(buttonStatus: Int, comboIndex: Int = 0) {
        val comboArray = when (comboIndex) {
            0 -> IntListSetting.COMBO_BUTTON_BUTTONS.list
            1 -> IntListSetting.COMBO_BUTTON_BUTTONS_2.list
            2 -> IntListSetting.COMBO_BUTTON_BUTTONS_3.list
            3 -> IntListSetting.COMBO_BUTTON_BUTTONS_4.list
            4 -> IntListSetting.COMBO_BUTTON_BUTTONS_5.list
            else -> return
        }
        for (nativeButton in comboArray) {
            if (nativeButton == -1) {
                // We don't want to parse any bad inputs here so we continue loop
                continue
            } else {
                NativeLibrary.onGamePadEvent(
                    NativeLibrary.TOUCHSCREEN_DEVICE,
                    nativeButton,
                    buttonStatus
                )
            }
        }
    }
}
