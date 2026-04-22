package com.safesteps.auth

data class UserInfo(
    val username: String,
    val email: String,
    val googleId: String,
    val photoUrl: String? = null
)

data class AuthUiState(
    val currentUser: UserInfo? = null
)
