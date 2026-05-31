package com.example.myproductivityapp.auth

import com.example.myproductivityapp.data.cloudbase.CloudBaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val userId: String) : AuthState()
}

class AuthManager(private val client: CloudBaseClient) {

    private val _authState = MutableStateFlow<AuthState>(
        if (client.accessToken.isNotBlank()) AuthState.LoggedIn(client.userId)
        else AuthState.LoggedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun signInAnonymously(): Result<String> = withContext(Dispatchers.IO) {
        _authState.value = AuthState.Loading
        val result = client.signInAnonymously()
        if (result.isSuccess) {
            _authState.value = AuthState.LoggedIn(result.getOrDefault(""))
        } else {
            _authState.value = AuthState.LoggedOut
        }
        result
    }

    fun signOut() {
        _authState.value = AuthState.LoggedOut
    }

    fun currentUserId(): String = client.userId

    fun isLoggedIn(): Boolean = client.accessToken.isNotBlank()
}
