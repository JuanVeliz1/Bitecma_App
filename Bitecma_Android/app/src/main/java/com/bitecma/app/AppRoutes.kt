package com.bitecma.app

import android.net.Uri

object AppRoutes {
    const val LOGIN = "login"
    const val FORGOT_PASSWORD_PATTERN = "forgot_password/{email}"
    const val DASHBOARD_PATTERN = "dashboard/{userId}"
    const val OPERACIONES_PATTERN = "operaciones/{userId}"
    const val ESPECIES_PATTERN = "especies/{userId}"
    const val BOTES_PATTERN = "botes/{userId}"
    const val ADMIN_PATTERN = "admin/{userId}"

    private const val EMPTY_EMAIL_ARG = "_"

    fun dashboard(userId: Int) = "dashboard/$userId"
    fun operaciones(userId: Int) = "operaciones/$userId"
    fun especies(userId: Int) = "especies/$userId"
    fun botes(userId: Int) = "botes/$userId"
    fun admin(userId: Int) = "admin/$userId"

    fun forgotPassword(email: String?): String {
        val normalized = email?.trim().takeUnless { it.isNullOrEmpty() } ?: EMPTY_EMAIL_ARG
        return "forgot_password/${Uri.encode(normalized)}"
    }

    fun decodeForgotPasswordEmail(encoded: String?): String {
        val decoded = Uri.decode(encoded.orEmpty())
        return if (decoded == EMPTY_EMAIL_ARG) "" else decoded
    }
}
