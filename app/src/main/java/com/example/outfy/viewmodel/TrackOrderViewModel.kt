package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.OrderRepository
import com.example.outfy.data.ReviewRepository
import com.example.outfy.model.OrderRecord
import kotlinx.coroutines.launch

class TrackOrderViewModel : ViewModel() {
    private val repository = OrderRepository()
    private val reviewRepository = ReviewRepository()

    private val _order = MutableLiveData<OrderRecord?>()
    val order: LiveData<OrderRecord?> = _order

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isCancelling = MutableLiveData(false)
    val isCancelling: LiveData<Boolean> = _isCancelling

    private val _isReordering = MutableLiveData(false)
    val isReordering: LiveData<Boolean> = _isReordering

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _openCartEvent = MutableLiveData(false)
    val openCartEvent: LiveData<Boolean> = _openCartEvent

    private val _reviewedProductIds = MutableLiveData<Set<String>>(emptySet())
    val reviewedProductIds: LiveData<Set<String>> = _reviewedProductIds

    fun loadOrder(orderId: String) {
        if (orderId.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val order = repository.getOrderById(orderId)
                _order.value = order
                _reviewedProductIds.value = if (order?.status == "delivered") {
                    reviewRepository.getReviewedProductIds(order.items.map { it.productId })
                } else {
                    emptySet()
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load order"
                _reviewedProductIds.value = emptySet()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun cancelOrder(orderId: String) {
        if (orderId.isBlank() || _isCancelling.value == true) return

        viewModelScope.launch {
            _isCancelling.value = true
            try {
                _message.value = repository.cancelOrder(orderId)
                val order = repository.getOrderById(orderId)
                _order.value = order
                _reviewedProductIds.value = if (order?.status == "delivered") {
                    reviewRepository.getReviewedProductIds(order.items.map { it.productId })
                } else {
                    emptySet()
                }
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to cancel order"
            } finally {
                _isCancelling.value = false
            }
        }
    }

    fun reorder(orderId: String) {
        if (orderId.isBlank() || _isReordering.value == true) return

        viewModelScope.launch {
            _isReordering.value = true
            try {
                _message.value = repository.reorderOrder(orderId)
                _openCartEvent.value = true
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to reorder"
            } finally {
                _isReordering.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearOpenCartEvent() {
        _openCartEvent.value = false
    }
}
