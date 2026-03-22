package com.simplstudios.simplstream.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simplstudios.simplstream.domain.model.VideoServerId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "simplstream_prefs")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private val KEY_CURRENT_PROFILE_ID = longPreferencesKey("current_profile_id")
        private val KEY_LAST_SEARCH_QUERY = stringPreferencesKey("last_search_query")
        private val KEY_DEFAULT_SERVER = stringPreferencesKey("default_server")
        private val KEY_PARENTAL_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        private val KEY_IS_KIDS_PROFILE = stringPreferencesKey("is_kids_profile")
        private const val KEY_PROFILE_LAST_LOGIN_PREFIX = "profile_last_login_"

        const val NO_PROFILE: Long = -1L
        private const val WELCOME_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }
    
    val currentProfileId: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_CURRENT_PROFILE_ID] ?: NO_PROFILE
    }
    
    val lastSearchQuery: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SEARCH_QUERY] ?: ""
    }
    
    val defaultServer: Flow<VideoServerId?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_SERVER]?.let { 
            try { 
                VideoServerId.valueOf(it) 
            } catch (e: Exception) { 
                null 
            }
        }
    }
    
    /**
     * Whether the current active profile is a kids profile.
     */
    val isKidsProfile: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_KIDS_PROFILE] == "true"
    }

    suspend fun isKidsProfileSync(): Boolean {
        return isKidsProfile.first()
    }

    suspend fun setKidsProfile(isKids: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_KIDS_PROFILE] = if (isKids) "true" else "false"
        }
    }

    // ==================== PARENTAL PIN ====================

    /**
     * Whether a parental PIN has been set.
     */
    suspend fun hasParentalPin(): Boolean {
        return context.dataStore.data.first()[KEY_PARENTAL_PIN_HASH] != null
    }

    /**
     * Set (or update) the global parental PIN.
     */
    suspend fun setParentalPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PARENTAL_PIN_HASH] = hashPin(pin)
        }
    }

    /**
     * Remove the parental PIN.
     */
    suspend fun clearParentalPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PARENTAL_PIN_HASH)
        }
    }

    /**
     * Verify the parental PIN.
     */
    suspend fun verifyParentalPin(pin: String): Boolean {
        val storedHash = context.dataStore.data.first()[KEY_PARENTAL_PIN_HASH] ?: return false
        return hashPin(pin) == storedHash
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun getDefaultServerSync(): VideoServerId? {
        return defaultServer.first()
    }
    
    suspend fun setCurrentProfile(profileId: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_PROFILE_ID] = profileId
        }
    }
    
    suspend fun clearCurrentProfile() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CURRENT_PROFILE_ID)
        }
    }
    
    suspend fun setLastSearchQuery(query: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SEARCH_QUERY] = query
        }
    }
    
    suspend fun setDefaultServer(serverId: VideoServerId?) {
        context.dataStore.edit { prefs ->
            if (serverId != null) {
                prefs[KEY_DEFAULT_SERVER] = serverId.name
            } else {
                prefs.remove(KEY_DEFAULT_SERVER)
            }
        }
    }
    
    /**
     * Check if welcome animation should play for this profile.
     * Returns true if it's the first login ever, or if it's been 7+ days.
     */
    suspend fun shouldShowWelcome(profileId: Long): Boolean {
        val key = longPreferencesKey("${KEY_PROFILE_LAST_LOGIN_PREFIX}$profileId")
        val lastLogin = context.dataStore.data.first()[key] ?: return true
        return (System.currentTimeMillis() - lastLogin) >= WELCOME_COOLDOWN_MS
    }

    /**
     * Record that the user just logged into this profile.
     */
    suspend fun recordProfileLogin(profileId: Long) {
        val key = longPreferencesKey("${KEY_PROFILE_LAST_LOGIN_PREFIX}$profileId")
        context.dataStore.edit { prefs ->
            prefs[key] = System.currentTimeMillis()
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
