package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.WishlistRepository
import com.example.outfy.model.Product
import kotlinx.coroutines.launch

class WishlistViewModel : ViewModel() {
    private val repository = WishlistRepository()

    private val _products = MutableLiveData<List<Product>>(emptyList())
    val products: LiveData<List<Product>> = _products

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadWishlist() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _products.value = repository.getWishlistProducts()
            } catch (e: Exception) {
                _products.value = emptyList()
                _errorMessage.value = e.message ?: "Unable to load wishlist"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
