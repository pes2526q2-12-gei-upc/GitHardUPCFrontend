package com.safesteps.auth

data class UserInfo(
    val username: String,
    val email: String,
    val googleId: String,
    val photoUrl: String? = null
)

enum class AuthNoticeMessage {
    LOGIN_SUCCESS,
    REGISTER_SUCCESS,
    SERVER_ERROR
}

data class AuthNotice(
    val id: Long,
    val message: AuthNoticeMessage
)

data class AuthUiState(
    val currentUser: UserInfo? = null,
    val authNotice: AuthNotice? = null
)
