package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.AuthRepository
import com.example.outfy.model.Users
import kotlinx.coroutines.launch

class SecurityViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _profile = MutableLiveData<Users?>()
    val profile: LiveData<Users?> = _profile

    private val _providerIds = MutableLiveData<List<String>>(emptyList())
    val providerIds: LiveData<List<String>> = _providerIds

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isSending = MutableLiveData(false)
    val isSending: LiveData<Boolean> = _isSending

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun loadSecurityState() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                _profile.value = repository.getUserProfile(uid)
                _providerIds.value = repository.getCurrentProviderIds()
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load security settings"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendResetEmail() {
        if (_isSending.value == true) return

        viewModelScope.launch {
            _isSending.value = true
            try {
                _message.value = repository.sendPasswordResetForCurrentUser()
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to start password reset"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
