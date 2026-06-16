package com.pda.app.data.api.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: UserInfoDto
)

/** Mirrors RMA's UserInfoDto — only fields the PDA app needs. */
@Serializable
data class UserInfoDto(
    val userId: String,
    val username: String,
    val email: String,
    val fullName: String,
    val phoneNumber: String? = null,
    val roles: List<String>? = null,
    val allowedPages: List<String>? = null
)

/** Returned by the server when login fails (400 / 401 / 403). */
@Serializable
data class ErrorResponse(val error: String)
