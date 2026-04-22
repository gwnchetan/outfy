package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.OrderRepository
import com.example.outfy.model.OrderRecord
import kotlinx.coroutines.launch

class OrdersViewModel : ViewModel() {
    private val repository = OrderRepository()

    private val _orders = MutableLiveData<List<OrderRecord>>(emptyList())
    val orders: LiveData<List<OrderRecord>> = _orders

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun loadOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _orders.value = repository.getOrders()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unable to load orders"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
