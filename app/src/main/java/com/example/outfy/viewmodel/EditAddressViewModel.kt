package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.AuthRepository
import com.example.outfy.model.UserAddress
import com.example.outfy.model.Users
import com.example.outfy.model.isComplete
import kotlinx.coroutines.launch

class EditAddressViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _address = MutableLiveData(UserAddress())
    val address: LiveData<UserAddress> = _address

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

    fun loadAddress() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                val profile = repository.getUserProfile(uid)
                _profile.value = profile
                val savedAddress = profile?.defaultAddress
                _address.value = if (savedAddress != null && savedAddress.isComplete()) {
                    savedAddress
                } else {
                    UserAddress(
                        fullName = profile?.name.orEmpty(),
                        phone = profile?.phone.orEmpty(),
                        label = "HOME"
                    )
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load saved address"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveAddress(address: UserAddress) {
        if (_isSaving.value == true) return

        if (!address.isComplete()) {
            _message.value = "Complete the address before saving."
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                val uid = repository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                val updatedProfile = repository.updateDefaultAddress(uid, address)
                _profile.value = updatedProfile
                _address.value = updatedProfile?.defaultAddress ?: address
                _message.value = "Saved address updated"
                _saveCompleted.value = true
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to save address"
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
