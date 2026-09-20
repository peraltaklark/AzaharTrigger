// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version.
// Refer to the license.txt file included.

package org.citra.citra_emu.features.touchinput

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.citra.citra_emu.CitraApplication
import org.json.JSONArray
import org.json.JSONException

/**
 * Stores named sets of touch input bindings. The Default profile always exists and can't be
 * renamed or deleted.
 */
class TouchInputBindingProfileManager(context: Context) {
    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(
            context.applicationContext ?: CitraApplication.appContext
        )

    init {
        val profiles = getProfiles()
        if (!profiles.contains(DEFAULT_PROFILE)) {
            saveProfilesList(listOf(DEFAULT_PROFILE) + profiles)
            if (!hasProfileData(DEFAULT_PROFILE)) {
                saveProfile(DEFAULT_PROFILE, emptyList())
            }
        }
    }

    fun getCurrentProfile(): String =
        preferences.getString(KEY_CURRENT_PROFILE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    fun setCurrentProfile(profileName: String) {
        preferences.edit().putString(KEY_CURRENT_PROFILE, profileName).apply()
    }

    fun getProfiles(): List<String> {
        val json = preferences.getString(KEY_PROFILES_LIST, null)
            ?: return listOf(DEFAULT_PROFILE)

        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (_: JSONException) {
            listOf(DEFAULT_PROFILE)
        }
    }

    fun createProfile(profileName: String): Boolean {
        if (profileName.isBlank()) return false

        val profiles = getProfiles()
        if (profiles.contains(profileName)) return false

        saveProfilesList(profiles + profileName)
        saveProfile(profileName, emptyList())
        return true
    }

    fun deleteProfile(profileName: String): Boolean {
        if (profileName == DEFAULT_PROFILE) return false

        val profiles = getProfiles()
        if (!profiles.contains(profileName)) return false

        saveProfilesList(profiles - profileName)
        preferences.edit().remove(getStorageKey(profileName)).apply()

        if (getCurrentProfile() == profileName) {
            setCurrentProfile(DEFAULT_PROFILE)
        }
        return true
    }

    fun renameProfile(oldName: String, newName: String): Boolean {
        if (oldName == DEFAULT_PROFILE || newName.isBlank() || oldName == newName) return false

        val profiles = getProfiles()
        val index = profiles.indexOf(oldName)
        if (index == -1 || profiles.contains(newName)) return false

        val bindings = loadProfile(oldName)

        saveProfilesList(profiles.toMutableList().also { it[index] = newName })
        saveProfile(newName, bindings)
        preferences.edit().remove(getStorageKey(oldName)).apply()

        if (getCurrentProfile() == oldName) {
            setCurrentProfile(newName)
        }
        return true
    }

    fun saveProfile(profileName: String, bindings: List<TouchInputBinding>) {
        preferences.edit()
            .putString(getStorageKey(profileName), TouchInputBinding.listToJson(bindings))
            .apply()
    }

    fun loadProfile(profileName: String): List<TouchInputBinding> =
        TouchInputBinding.listFromJson(preferences.getString(getStorageKey(profileName), null))

    private fun saveProfilesList(profiles: List<String>) {
        val array = JSONArray()
        profiles.forEach { array.put(it) }
        preferences.edit().putString(KEY_PROFILES_LIST, array.toString()).apply()
    }

    private fun hasProfileData(profileName: String): Boolean =
        preferences.contains(getStorageKey(profileName))

    private fun getStorageKey(profileName: String): String = "$KEY_BINDINGS_PREFIX$profileName"

    companion object {
        const val DEFAULT_PROFILE = "Default"

        private const val KEY_CURRENT_PROFILE = "current_profile"
        private const val KEY_PROFILES_LIST = "profiles_list"
        private const val KEY_BINDINGS_PREFIX = "bindings_profile_"
    }
}
