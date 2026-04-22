package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.AuthRepository
import com.example.outfy.model.Users
import kotlinx.coroutines.launch

class EditProfileViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _profile = MutableLiveData<Users?>()
    val profile: LiveData<Users?> = _profile

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _saveCompleted = MutableLiveData(false)
    val saveCompleted: LiveData<Boolean> = _saveCompleted

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                _profile.value = repository.getUserProfile(uid)
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveProfile(
        name: String,
        gender: String,
        dob: String?
    ) {
        if (_isSaving.value == true) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                _profile.value = repository.updateUserProfile(uid, name, gender, dob)
                _message.value = "Profile updated successfully"
                _saveCompleted.value = true
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to update profile"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearSaveCompleted() {
        _saveCompleted.value = false
    }
}
