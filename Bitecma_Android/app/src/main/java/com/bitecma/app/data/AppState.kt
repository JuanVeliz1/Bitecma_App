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
    
    var currentUserId by mutableStateOf<Int?>(null)

    var authToken by mutableStateOf<String?>(null)

    var currentUserName by mutableStateOf<String?>(null)
    var currentUserRole by mutableStateOf<String?>(null)

    private const val PREFS = "bitecma_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_UID = "auth_uid"
    private const val KEY_NAME = "auth_name"
    private const val KEY_ROLE = "auth_role"

    private fun lastLoginKey(email: String): String = "last_login_" + email.trim().lowercase()

    fun loadSession(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        authToken = sp.getString(KEY_TOKEN, null)
        val uid = sp.getInt(KEY_UID, -1)
        currentUserId = if (uid > 0) uid else null
        currentUserName = sp.getString(KEY_NAME, null)
        currentUserRole = sp.getString(KEY_ROLE, null)
        isOnline = !authToken.isNullOrBlank()
    }

    fun persistSession(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ed = sp.edit()
        val token = authToken
        if (token.isNullOrBlank()) ed.remove(KEY_TOKEN) else ed.putString(KEY_TOKEN, token)
        val uid = currentUserId
        if (uid == null || uid <= 0) ed.remove(KEY_UID) else ed.putInt(KEY_UID, uid)
        val name = currentUserName
        if (name.isNullOrBlank()) ed.remove(KEY_NAME) else ed.putString(KEY_NAME, name)
        val role = currentUserRole
        if (role.isNullOrBlank()) ed.remove(KEY_ROLE) else ed.putString(KEY_ROLE, role)
        ed.apply()
    }

    fun clearSession(context: Context) {
        authToken = null
        currentUserId = null
        currentUserName = null
        currentUserRole = null
        isOnline = false
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit().remove(KEY_TOKEN).remove(KEY_UID).remove(KEY_NAME).remove(KEY_ROLE).apply()
    }

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
}
