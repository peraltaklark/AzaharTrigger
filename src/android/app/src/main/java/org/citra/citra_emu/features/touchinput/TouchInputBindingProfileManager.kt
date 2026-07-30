// Copyright 2025 Azahar Project
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
            CitraApplication.appContext
        )

    companion object {
        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PROFILES_LIST = "profiles_list"
        private const val KEY_BINDINGS_PREFIX = "bindings_profile_"
        private const val DEFAULT_PROFILE = "Default"
    }

    init {
        if (!getProfiles().contains(DEFAULT_PROFILE)) {
            saveProfile(DEFAULT_PROFILE, emptyList())
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
        val json = preferences.getString(KEY_PROFILES_LIST, null)

        return if (json != null) {
            try {
                val array = JSONArray(json)
                List(array.length()) { index ->
                    array.getString(index)
                }
            } catch (_: Exception) {
                listOf(DEFAULT_PROFILE)
            }
        } else {
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
        profiles.remove(profileName)
        saveProfilesList(profiles)

        preferences.edit()
            .remove("$KEY_BINDINGS_PREFIX$profileName")
            .apply()

        if (getCurrentProfile() == profileName) {
            setCurrentProfile(DEFAULT_PROFILE)
        }

        return true
    }

    fun renameProfile(oldName: String, newName: String): Boolean {
        if (oldName == DEFAULT_PROFILE || getProfiles().contains(newName)) {
            return false
        }

        val bindings = loadProfile(oldName)

        deleteProfile(oldName)
        createProfile(newName)
        saveProfile(newName, bindings)

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
                put("keyCode", binding.keyCode)
                put("axis", binding.axis)
                put("positive", binding.positive)
                put("analog", binding.analog)
                put("threshold", binding.threshold)
                put("x", binding.x)
                put("y", binding.y)
            }

            array.put(obj)
        }

        preferences.edit()
            .putString("$KEY_BINDINGS_PREFIX$profileName", array.toString())
            .apply()
    }

    fun loadProfile(profileName: String): List<TouchInputBinding> {
        val json = preferences.getString(
            "$KEY_BINDINGS_PREFIX$profileName",
            null
        ) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)

                TouchInputBinding(
                    keyCode = obj.optInt("keyCode", -1),
                    axis = obj.optInt("axis", -1),
                    positive = obj.optBoolean("positive", true),
                    analog = obj.optBoolean("analog", false),
                    threshold = obj.optDouble("threshold", 0.5).toFloat(),
                    x = obj.optDouble("x", 0.5).toFloat(),
                    y = obj.optDouble("y", 0.5).toFloat()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}