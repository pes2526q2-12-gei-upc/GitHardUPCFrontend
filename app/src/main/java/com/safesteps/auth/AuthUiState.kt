package com.safesteps.auth

data class UserInfo(
    val username: String,
    val email: String,
    val photoUrl: String? = null,
    val idToken: String? = null
)

data class AuthUiState(
    val currentUser: UserInfo? = null
)
