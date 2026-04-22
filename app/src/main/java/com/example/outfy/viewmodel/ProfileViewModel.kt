package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.AuthRepository
import com.example.outfy.model.Users
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _profile = MutableLiveData<Users?>()
    val profile: LiveData<Users?> = _profile

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                if (uid == null) {
                    _profile.value = null
                    _errorMessage.value = "Session expired. Please sign in again."
                } else {
                    _profile.value = repository.getUserProfile(uid)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unable to load profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout(
        googleSignInClient: GoogleSignInClient? = null,
        onComplete: () -> Unit
    ) {
        repository.signOut(googleSignInClient, onComplete)
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
