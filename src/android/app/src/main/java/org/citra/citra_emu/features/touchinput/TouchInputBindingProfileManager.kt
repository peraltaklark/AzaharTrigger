// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.json.JSONArray
import org.json.JSONObject

class TouchInputBindingProfileManager(context: Context) {

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext ?: CitraApplication.appContext
        )

    companion object {
        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PROFILES_LIST = "profiles_list"
        private const val KEY_BINDINGS_PREFIX = "bindings_profile_"
        private const val DEFAULT_PROFILE = "Default"

        // JSON Serialization Keys
        private const val JSON_KEY_CODE = "keyCode"
        private const val JSON_AXIS = "axis"
        private const val JSON_POSITIVE = "positive"
        private const val JSON_ANALOG = "analog"
        private const val JSON_THRESHOLD = "threshold"
        private const val JSON_X = "x"
        private const val JSON_Y = "y"
    }

    init {
        val currentProfiles = getProfiles()
        if (!currentProfiles.contains(DEFAULT_PROFILE)) {
            val updatedProfiles = currentProfiles.toMutableList().apply {
                add(0, DEFAULT_PROFILE)
            }
            saveProfilesList(updatedProfiles)
            if (!hasProfileData(DEFAULT_PROFILE)) {
                saveProfile(DEFAULT_PROFILE, emptyList())
            }
        }
    }

    fun getCurrentProfile(): String {
        return preferences.getString(
            KEY_CURRENT_PROFILE,
            DEFAULT_PROFILE
        ) ?: DEFAULT_PROFILE
    }

    fun setCurrentProfile(profileName: String) {
        preferences.edit()
            .putString(KEY_CURRENT_PROFILE, profileName)
            .apply()
    }

    fun getProfiles(): List<String> {
        val json = preferences.getString(KEY_PROFILES_LIST, null) ?: return listOf(DEFAULT_PROFILE)

        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                array.getString(index)
            }
        } catch (_: Exception) {
            listOf(DEFAULT_PROFILE)
        }
    }

    private fun saveProfilesList(profiles: List<String>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(profile)
        }

        preferences.edit()
            .putString(KEY_PROFILES_LIST, array.toString())
            .apply()
    }

    fun createProfile(profileName: String): Boolean {
        if (profileName.isBlank()) return false

        val profiles = getProfiles().toMutableList()
        if (profiles.contains(profileName)) {
            return false
        }

        profiles.add(profileName)
        saveProfilesList(profiles)
        saveProfile(profileName, emptyList())

        return true
    }

    fun deleteProfile(profileName: String): Boolean {
        if (profileName == DEFAULT_PROFILE) {
            return false
        }

        val profiles = getProfiles().toMutableList()
        if (!profiles.contains(profileName)) {
            return false
        }

        profiles.remove(profileName)
        saveProfilesList(profiles)

        preferences.edit()
            .remove(getProfileStorageKey(profileName))
            .apply()

        if (getCurrentProfile() == profileName) {
            setCurrentProfile(DEFAULT_PROFILE)
        }

        return true
    }

    fun renameProfile(oldName: String, newName: String): Boolean {
        if (oldName == DEFAULT_PROFILE || newName.isBlank() || oldName == newName) {
            return false
        }

        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOf(oldName)
        if (index == -1 || profiles.contains(newName)) {
            return false
        }

        val bindings = loadProfile(oldName)

        // Atomic update: rename entry in list and update keys
        profiles[index] = newName
        saveProfilesList(profiles)

        saveProfile(newName, bindings)
        preferences.edit()
            .remove(getProfileStorageKey(oldName))
            .apply()

        if (getCurrentProfile() == oldName) {
            setCurrentProfile(newName)
        }

        return true
    }

    fun saveProfile(
        profileName: String,
        bindings: List<TouchInputBinding>
    ) {
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
            .putString(getProfileStorageKey(profileName), array.toString())
            .apply()
    }

    fun loadProfile(profileName: String): List<TouchInputBinding> {
        val json = preferences.getString(
            getProfileStorageKey(profileName),
            null
        ) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)

                TouchInputBinding(
                    keyCode = obj.optInt(JSON_KEY_CODE, -1),
                    axis = obj.optInt(JSON_AXIS, -1),
                    positive = obj.optBoolean(JSON_POSITIVE, true),
                    analog = obj.optBoolean(JSON_ANALOG, false),
                    threshold = obj.optDouble(JSON_THRESHOLD, 0.5).toFloat(),
                    x = obj.optDouble(JSON_X, 0.5).toFloat(),
                    y = obj.optDouble(JSON_Y, 0.5).toFloat()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun hasProfileData(profileName: String): Boolean {
        return preferences.contains(getProfileStorageKey(profileName))
    }

    private fun getProfileStorageKey(profileName: String): String {
        return "$KEY_BINDINGS_PREFIX$profileName"
    }
}
