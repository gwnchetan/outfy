package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.CartRepository
import com.example.outfy.model.CartItem
import com.example.outfy.model.CartPricing
import com.example.outfy.model.hasStockIssue
import com.example.outfy.model.lineTotal
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    private val cartRepository = CartRepository()

    private val _cartItems = MutableLiveData<List<CartItem>>(emptyList())
    val cartItems: LiveData<List<CartItem>> = _cartItems

    private val _subtotal = MutableLiveData(0.0)
    val subtotal: LiveData<Double> = _subtotal

    private val _shippingFee = MutableLiveData(0.0)
    val shippingFee: LiveData<Double> = _shippingFee

    private val _total = MutableLiveData(0.0)
    val total: LiveData<Double> = _total

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _canPlaceOrder = MutableLiveData(false)
    val canPlaceOrder: LiveData<Boolean> = _canPlaceOrder

    private val _cartCount = MutableLiveData(0)
    val cartCount: LiveData<Int> = _cartCount

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun loadCart() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = cartRepository.getCartItems()
                _cartItems.value = items
                updateSummary(items)
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load cart"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCartCount() {
        viewModelScope.launch {
            try {
                val count = cartRepository.getCartCount()
                _cartCount.value = count
            } catch (e: Exception) {
                // Ignore errors for count
            }
        }
    }

    fun increaseQuantity(item: CartItem) {
        updateQuantity(item, item.quantity + 1)
    }

    fun decreaseQuantity(item: CartItem) {
        updateQuantity(item, item.quantity - 1)
    }

    fun removeItem(item: CartItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                cartRepository.removeItem(item.cartItemId)
                loadCart()
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to remove item"
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun updateQuantity(item: CartItem, quantity: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                cartRepository.updateQuantity(item.cartItemId, quantity)
                val refreshed = cartRepository.getCartItems()
                _cartItems.value = refreshed
                updateSummary(refreshed)
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to update cart"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateSummary(items: List<CartItem>) {
        val subtotal = items.sumOf { it.lineTotal() }
        _subtotal.value = subtotal
        _shippingFee.value = CartPricing.deliveryCharge(subtotal)
        _total.value = CartPricing.totalAmount(subtotal)
        updateCheckoutState(items)
        _cartCount.value = items.sumOf { it.quantity }
    }

    private fun updateCheckoutState(items: List<CartItem>) {
        _canPlaceOrder.value =
            items.isNotEmpty() && items.none { it.hasStockIssue() }
    }
}
