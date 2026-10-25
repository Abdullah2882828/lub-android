package com.lub.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "lub_settings")

class SettingsStore(private val context: Context) {

    companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode") // "SYSTEM", "LIGHT", "DARK"
        val KEY_GITHUB_TOKEN_ENCRYPTED = stringPreferencesKey("github_token_encrypted")
        val KEY_GITHUB_USERNAME = stringPreferencesKey("github_username")
        val KEY_FAST_PUSH_NO_CONFIRM = booleanPreferencesKey("fast_push_no_confirm")
        val KEY_DEFAULT_CAPTURE_MODE = stringPreferencesKey("default_capture_mode") // "FULL_CHAT", "SCREEN"
        val KEY_AUTO_SCROLL_LIMIT = intPreferencesKey("auto_scroll_limit")
        val KEY_DENY_LIST = stringSetPreferencesKey("deny_list")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val KEY_BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val KEY_GEMINI_KEY_ENCRYPTED = stringPreferencesKey("gemini_key_encrypted")

        // Default DenyList apps: Banking, Passwords, Authenticator, System Settings
        val DEFAULT_DENY_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.apps.authenticator2",
            "com.onepassword.android",
            "com.lastpass.lpandroid",
            "com.bitwarden",
            "com.dashlane",
            "com.paypal.android.p2pmobile",
            "com.google.android.apps.walletnfcrel"
        )
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING_COMPLETED] ?: false
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_THEME_MODE] ?: "SYSTEM"
    }

    val fastPushFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_FAST_PUSH_NO_CONFIRM] ?: false
    }

    val defaultCaptureModeFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_DEFAULT_CAPTURE_MODE] ?: "FULL_CHAT"
    }

    val autoScrollLimitFlow: Flow<Int> = context.dataStore.data.map {
        it[KEY_AUTO_SCROLL_LIMIT] ?: 50
    }

    val denyListFlow: Flow<Set<String>> = context.dataStore.data.map {
        it[KEY_DENY_LIST] ?: DEFAULT_DENY_PACKAGES
    }

    val biometricLockFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_BIOMETRIC_LOCK] ?: false
    }

    val bubbleEnabledFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_BUBBLE_ENABLED] ?: true
    }

    val githubUsernameFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_GITHUB_USERNAME] ?: ""
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setFastPush(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FAST_PUSH_NO_CONFIRM] = enabled }
    }

    suspend fun setDefaultCaptureMode(mode: String) {
        context.dataStore.edit { it[KEY_DEFAULT_CAPTURE_MODE] = mode }
    }

    suspend fun setAutoScrollLimit(limit: Int) {
        context.dataStore.edit { it[KEY_AUTO_SCROLL_LIMIT] = limit }
    }

    suspend fun setBubbleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BUBBLE_ENABLED] = enabled }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setDenyList(packages: Set<String>) {
        context.dataStore.edit { it[KEY_DENY_LIST] = packages }
    }

    suspend fun addDenyPackage(pkg: String) {
        context.dataStore.edit {
            val current = it[KEY_DENY_LIST]?.toMutableSet() ?: DEFAULT_DENY_PACKAGES.toMutableSet()
            current.add(pkg)
            it[KEY_DENY_LIST] = current
        }
    }

    suspend fun removeDenyPackage(pkg: String) {
        context.dataStore.edit {
            val current = it[KEY_DENY_LIST]?.toMutableSet() ?: DEFAULT_DENY_PACKAGES.toMutableSet()
            current.remove(pkg)
            it[KEY_DENY_LIST] = current
        }
    }

    suspend fun saveGitHubToken(token: String, username: String = "") {
        val encrypted = KeystoreCrypto.encrypt(token)
        context.dataStore.edit {
            it[KEY_GITHUB_TOKEN_ENCRYPTED] = encrypted
            if (username.isNotEmpty()) {
                it[KEY_GITHUB_USERNAME] = username
            }
        }
    }

    suspend fun getGitHubToken(): String {
        val encrypted = context.dataStore.data.first()[KEY_GITHUB_TOKEN_ENCRYPTED] ?: ""
        return if (encrypted.isNotEmpty()) KeystoreCrypto.decrypt(encrypted) else ""
    }

    suspend fun clearGitHubToken() {
        context.dataStore.edit {
            it.remove(KEY_GITHUB_TOKEN_ENCRYPTED)
            it.remove(KEY_GITHUB_USERNAME)
        }
    }

    suspend fun saveGeminiApiKey(key: String) {
        val encrypted = KeystoreCrypto.encrypt(key)
        context.dataStore.edit { it[KEY_GEMINI_KEY_ENCRYPTED] = encrypted }
    }

    suspend fun getGeminiApiKey(): String {
        val encrypted = context.dataStore.data.first()[KEY_GEMINI_KEY_ENCRYPTED] ?: ""
        return if (encrypted.isNotEmpty()) KeystoreCrypto.decrypt(encrypted) else ""
    }
}
