package com.example.myproductivityapp.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val client: CloudBaseClient) : ViewModel() {
    val authManager = AuthManager(client)
    val authState: StateFlow<AuthState> = authManager.authState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun signInAnonymously() {
        viewModelScope.launch {
            val result = authManager.signInAnonymously()
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "登录失败"
            } else {
                _errorMessage.value = null
            }
        }
    }
}
