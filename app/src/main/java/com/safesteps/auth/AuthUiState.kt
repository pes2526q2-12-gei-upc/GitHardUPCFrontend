package com.safesteps.auth

data class UserInfo(
    val username: String,
    val email: String,
    val googleId: String,
    val photoUrl: String? = null,
    val backendLanguageTag: String? = null,
    val routeColor: String? = null,
    val nameStyle: String? = "Normal"
)

enum class AuthNoticeMessage {
    LOGIN_SUCCESS,
    REGISTER_SUCCESS,
    ACCOUNT_BANNED,
    ACCOUNT_SUSPENDED,
    SERVER_ERROR,
    DELETE_ACCOUNT_SUCCESS,
    DELETE_ACCOUNT_ERROR
}

data class AuthNotice(
    val id: Long,
    val message: AuthNoticeMessage
)

data class AuthUiState(
    val currentUser: UserInfo? = null,
    val authNotice: AuthNotice? = null,
    val isDeletingAccount: Boolean = false,
    val pendingDeleteAccountSignOut: Boolean = false,
    val pendingAccessDeniedSignOut: Boolean = false
)
