package com.pda.app.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pda.app.data.NetworkResult
import com.pda.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PDA/LoginViewModel"
    }

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        Log.i(TAG, "login: triggered — username=$username")
        authRepository.login(username, password)
            .onEach { result ->
                val newState = when (result) {
                    is NetworkResult.Loading -> {
                        Log.d(TAG, "uiState → Loading")
                        LoginUiState.Loading
                    }
                    is NetworkResult.Success -> {
                        Log.i(TAG, "uiState → Success — user=${result.data.user.username}")
                        LoginUiState.Success(token = result.data.token, user = result.data.user)
                    }
                    is NetworkResult.Error -> {
                        Log.w(TAG, "uiState → Error — ${result.message} (code=${result.code})")
                        LoginUiState.Error(result.message)
                    }
                }
                _uiState.value = newState
            }
            .launchIn(viewModelScope)
    }

    fun clearError() {
        if (_uiState.value is LoginUiState.Error) {
            Log.d(TAG, "clearError: resetting to Idle")
            _uiState.value = LoginUiState.Idle
        }
    }
}
