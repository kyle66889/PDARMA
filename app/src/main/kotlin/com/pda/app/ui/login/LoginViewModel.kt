package com.pda.app.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.prefs.UserPreferences
import com.pda.app.data.repository.AuthRepository
import com.pda.app.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private companion object {
        const val TAG = "PDA/LoginViewModel"
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** 一次性预填值；null 表示尚未加载。 */
    private val _prefill = MutableStateFlow<LoginPrefill?>(null)
    val prefill: StateFlow<LoginPrefill?> = _prefill.asStateFlow()

    init {
        viewModelScope.launch {
            val remember = userPreferences.rememberUsername.first()
            val username = if (remember) userPreferences.lastUsername.first().orEmpty() else ""
            val password = if (remember) userPreferences.lastPassword.first().orEmpty() else ""
            _prefill.value = LoginPrefill(username, password, remember)
        }
    }

    fun login(username: String, password: String, rememberCredentials: Boolean) {
        Log.i(TAG, "login: triggered — username=$username, remember=$rememberCredentials")
        authRepository.login(username, password)
            .onEach { result ->
                when (result) {
                    is NetworkResult.Loading -> _uiState.value = LoginUiState.Loading
                    is NetworkResult.Success -> {
                        Log.i(TAG, "login success — user=${result.data.user.username}")
                        sessionManager.start(result.data.token, result.data.user)
                        userPreferences.saveLoginCredentials(
                            username.trim(),
                            password,
                            rememberCredentials
                        )
                        _uiState.value = LoginUiState.Success(result.data.token, result.data.user)
                    }
                    is NetworkResult.Error -> {
                        Log.w(TAG, "login error — ${result.message}")
                        _uiState.value = LoginUiState.Error(result.message)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }
}
