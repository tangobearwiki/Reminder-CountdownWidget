package com.ybhgl.reminder.data
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

private const val SECURITY_DATA_STORE_NAME = "security_preferences"

private val APP_LOCK_ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")
private val GESTURE_PASSWORD_KEY = stringPreferencesKey("gesture_password")
private val SCREENSHOT_BLOCKED_KEY = booleanPreferencesKey("screenshot_blocked")
private val USE_BIOMETRIC_KEY = booleanPreferencesKey("use_biometric")

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SECURITY_DATA_STORE_NAME
)

object AppLockState {
    var isUnlocked = androidx.compose.runtime.mutableStateOf(false)
}

object SecurityPreferences {

    fun appLockEnabledFlow(context: Context): Flow<Boolean> =
        context.securityDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[APP_LOCK_ENABLED_KEY] ?: false
            }

    suspend fun saveAppLockEnabled(context: Context, enabled: Boolean) {
        context.securityDataStore.edit { preferences ->
            preferences[APP_LOCK_ENABLED_KEY] = enabled
        }
        BackupPreferences.saveLastDataChangeTimestamp(context, System.currentTimeMillis())
    }

    fun gesturePasswordFlow(context: Context): Flow<String> =
        context.securityDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[GESTURE_PASSWORD_KEY] ?: ""
            }

    suspend fun saveGesturePassword(context: Context, password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hashGesturePassword(password, salt)
        context.securityDataStore.edit { preferences ->
            preferences[GESTURE_PASSWORD_KEY] = "$saltHex:$hashHex"
        }
        BackupPreferences.saveLastDataChangeTimestamp(context, System.currentTimeMillis())
    }

    private fun hashGesturePassword(password: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 校验手势密码。存储格式为 "saltHex:hashHex"。
     * 兼容旧版明文存储：若比对成功则自动迁移为加盐哈希。
     */
    suspend fun verifyGesturePassword(context: Context, password: String): Boolean {
        val stored = gesturePasswordFlow(context).first()
        if (stored.isEmpty()) return false
        return if (stored.contains(":")) {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = parts[0].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            hashGesturePassword(password, salt) == parts[1]
        } else {
            // 旧版明文，校验通过后迁移
            val match = stored == password
            if (match) saveGesturePassword(context, password)
            match
        }
    }

    fun screenshotBlockedFlow(context: Context): Flow<Boolean> =
        context.securityDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[SCREENSHOT_BLOCKED_KEY] ?: false
            }

    suspend fun saveScreenshotBlocked(context: Context, blocked: Boolean) {
        context.securityDataStore.edit { preferences ->
            preferences[SCREENSHOT_BLOCKED_KEY] = blocked
        }
        BackupPreferences.saveLastDataChangeTimestamp(context, System.currentTimeMillis())
    }

    fun useBiometricFlow(context: Context): Flow<Boolean> =
        context.securityDataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[USE_BIOMETRIC_KEY] ?: false
            }

    suspend fun saveUseBiometric(context: Context, use: Boolean) {
        context.securityDataStore.edit { preferences ->
            preferences[USE_BIOMETRIC_KEY] = use
        }
        BackupPreferences.saveLastDataChangeTimestamp(context, System.currentTimeMillis())
    }
}
