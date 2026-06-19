package com.bitecma.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppState {
    var isOnline by mutableStateOf(false)
    var hasNetwork by mutableStateOf(false)
    var lastSyncAtMs by mutableStateOf<Long?>(null)
    var lastSyncError by mutableStateOf<String?>(null)
    
    var currentUserId by mutableStateOf<Int?>(null)

    var authToken by mutableStateOf<String?>(null)

    var currentUserEmail by mutableStateOf<String?>(null)
    var currentUserName by mutableStateOf<String?>(null)
    var currentUserRole by mutableStateOf<String?>(null)
    var forceOffline by mutableStateOf(false)
    var hasVerifiedSession by mutableStateOf(false)
    var isGuestMode by mutableStateOf(false)

    private const val PREFS = "bitecma_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_UID = "auth_uid"
    private const val KEY_EMAIL = "auth_email"
    private const val KEY_NAME = "auth_name"
    private const val KEY_ROLE = "auth_role"
    private const val KEY_FORCE_OFFLINE = "force_offline"
    private const val KEY_VERIFIED_SESSION = "verified_session"
    private const val KEY_GUEST_MODE = "guest_mode"

    private fun lastLoginKey(email: String): String = "last_login_" + email.trim().lowercase()

    fun loadSession(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        authToken = sp.getString(KEY_TOKEN, null)
        val uid = sp.getInt(KEY_UID, -1)
        currentUserId = if (uid > 0) uid else null
        currentUserEmail = sp.getString(KEY_EMAIL, null)
        currentUserName = sp.getString(KEY_NAME, null)
        currentUserRole = sp.getString(KEY_ROLE, null)
        hasVerifiedSession = sp.getBoolean(KEY_VERIFIED_SESSION, false)
        isGuestMode = sp.getBoolean(KEY_GUEST_MODE, false)
        forceOffline = sp.getBoolean(KEY_FORCE_OFFLINE, false)
        if (!hasAuthenticatedSession()) {
            hasVerifiedSession = false
            forceOffline = false
            authToken = null
        }
        if (!hasAuthenticatedSession() && !isGuestMode) {
            currentUserId = null
            currentUserEmail = null
            currentUserName = null
            currentUserRole = null
        }
        hasNetwork = false
        isOnline = false
    }

    fun persistSession(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ed = sp.edit()
        val token = authToken
        if (token.isNullOrBlank()) ed.remove(KEY_TOKEN) else ed.putString(KEY_TOKEN, token)
        val uid = currentUserId
        if (uid == null || uid <= 0) ed.remove(KEY_UID) else ed.putInt(KEY_UID, uid)
        val email = currentUserEmail
        if (email.isNullOrBlank()) ed.remove(KEY_EMAIL) else ed.putString(KEY_EMAIL, email)
        val name = currentUserName
        if (name.isNullOrBlank()) ed.remove(KEY_NAME) else ed.putString(KEY_NAME, name)
        val role = currentUserRole
        if (role.isNullOrBlank()) ed.remove(KEY_ROLE) else ed.putString(KEY_ROLE, role)
        if (hasAuthenticatedSession()) {
            ed.putBoolean(KEY_VERIFIED_SESSION, true)
            ed.putBoolean(KEY_FORCE_OFFLINE, forceOffline)
            ed.remove(KEY_GUEST_MODE)
            isGuestMode = false
        } else if (isGuestMode) {
            ed.remove(KEY_TOKEN)
            ed.remove(KEY_UID)
            ed.remove(KEY_EMAIL)
            ed.remove(KEY_NAME)
            ed.remove(KEY_ROLE)
            ed.remove(KEY_VERIFIED_SESSION)
            ed.remove(KEY_FORCE_OFFLINE)
            ed.putBoolean(KEY_GUEST_MODE, true)
            forceOffline = false
        } else {
            hasVerifiedSession = false
            isGuestMode = false
            ed.remove(KEY_VERIFIED_SESSION)
            forceOffline = false
            ed.remove(KEY_FORCE_OFFLINE)
            ed.remove(KEY_GUEST_MODE)
        }
        ed.apply()
    }

    fun enterGuestMode(context: Context) {
        authToken = null
        currentUserId = null
        currentUserEmail = null
        currentUserName = "Modo sin cuenta"
        currentUserRole = null
        forceOffline = false
        hasVerifiedSession = false
        isGuestMode = true
        isOnline = false
        persistSession(context)
    }

    fun clearSession(context: Context) {
        authToken = null
        currentUserId = null
        currentUserEmail = null
        currentUserName = null
        currentUserRole = null
        forceOffline = false
        hasVerifiedSession = false
        isGuestMode = false
        hasNetwork = false
        isOnline = false
        lastSyncAtMs = null
        lastSyncError = null
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove(KEY_TOKEN).remove(KEY_UID).remove(KEY_EMAIL).remove(KEY_NAME).remove(KEY_ROLE).remove(KEY_FORCE_OFFLINE).remove(KEY_VERIFIED_SESSION).remove(KEY_GUEST_MODE).apply()
    }

    fun isEffectivelyOnline(): Boolean {
        return !forceOffline && hasNetwork && hasAuthenticatedSession() && !authToken.isNullOrBlank()
    }

    fun hasAuthenticatedSession(): Boolean {
        return hasVerifiedSession && currentUserId != null && !currentUserEmail.isNullOrBlank()
    }

    fun hasAppAccess(): Boolean {
        return hasAuthenticatedSession() || isGuestMode
    }

    fun dashboardUserId(): Int = currentUserId ?: 0


    fun saveLastLoginNow(context: Context, email: String) {
        val k = lastLoginKey(email)
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().putLong(k, System.currentTimeMillis()).apply()
    }

    fun getLastLoginText(context: Context, email: String): String? {
        val k = lastLoginKey(email)
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ms = sp.getLong(k, 0L)
        if (ms <= 0L) return null
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        return fmt.format(Instant.ofEpochMilli(ms))
    }

    fun registerSyncSuccess() {
        isOnline = true
        lastSyncError = null
        lastSyncAtMs = System.currentTimeMillis()
    }

    fun registerSyncFailure(message: String? = null) {
        lastSyncError = message?.trim()?.takeIf { it.isNotEmpty() }
    }
}
