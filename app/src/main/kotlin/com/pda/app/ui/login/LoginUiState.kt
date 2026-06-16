package com.pda.app.ui.login

import com.pda.app.data.api.model.UserInfoDto

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val token: String, val user: UserInfoDto) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

/** 登录界面初始预填值（来自 DataStore）。 */
data class LoginPrefill(
    val username: String,
    val password: String,
    val rememberCredentials: Boolean
)
