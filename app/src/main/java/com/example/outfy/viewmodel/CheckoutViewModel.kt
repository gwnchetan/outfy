package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.AuthRepository
import com.example.outfy.data.CartRepository
import com.example.outfy.data.OrderRepository
import com.example.outfy.model.CartItem
import com.example.outfy.model.CartPricing
import com.example.outfy.model.UserAddress
import com.example.outfy.model.hasStockIssue
import com.example.outfy.model.isComplete
import com.example.outfy.model.lineTotal
import kotlinx.coroutines.launch

class CheckoutViewModel : ViewModel() {
    private val authRepository = AuthRepository()
    private val cartRepository = CartRepository()
    private val orderRepository = OrderRepository()

    private val _cartItems = MutableLiveData<List<CartItem>>(emptyList())
    val cartItems: LiveData<List<CartItem>> = _cartItems

    private val _address = MutableLiveData(UserAddress())
    val address: LiveData<UserAddress> = _address

    private val _showAddressForm = MutableLiveData(true)
    val showAddressForm: LiveData<Boolean> = _showAddressForm

    private val _subtotal = MutableLiveData(0.0)
    val subtotal: LiveData<Double> = _subtotal

    private val _deliveryCharge = MutableLiveData(0.0)
    val deliveryCharge: LiveData<Double> = _deliveryCharge

    private val _tax = MutableLiveData(0.0)
    val tax: LiveData<Double> = _tax

    private val _total = MutableLiveData(0.0)
    val total: LiveData<Double> = _total

    private val _canPlaceOrder = MutableLiveData(false)
    val canPlaceOrder: LiveData<Boolean> = _canPlaceOrder

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isPlacingOrder = MutableLiveData(false)
    val isPlacingOrder: LiveData<Boolean> = _isPlacingOrder

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _orderPlacedId = MutableLiveData<String?>()
    val orderPlacedId: LiveData<String?> = _orderPlacedId

    fun loadCheckout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val uid = authRepository.getCurrentUser()?.uid
                    ?: throw IllegalStateException("Session expired. Please sign in again.")
                val profile = authRepository.getUserProfile(uid)
                val cartItems = cartRepository.getCartItems()
                _cartItems.value = cartItems

                val defaultAddress = profile?.defaultAddress
                _address.value = if (defaultAddress != null && defaultAddress.isComplete()) {
                    defaultAddress
                } else {
                    UserAddress(
                        fullName = profile?.name.orEmpty(),
                        phone = profile?.phone.orEmpty(),
                        label = "HOME"
                    )
                }

                _showAddressForm.value = _address.value?.isComplete() != true
                updatePricing(cartItems)
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to load checkout"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun showAddressEditor(show: Boolean) {
        _showAddressForm.value = show
    }

    fun placeOrder(address: UserAddress) {
        if (_isPlacingOrder.value == true) return

        if (!address.isComplete()) {
            _message.value = "Complete the delivery address before placing your order."
            _showAddressForm.value = true
            return
        }

        viewModelScope.launch {
            _isPlacingOrder.value = true
            try {
                val orderId = orderRepository.placeOrder(address)
                _address.value = address
                _showAddressForm.value = false
                _message.value = "Order placed successfully"
                _orderPlacedId.value = orderId
                _cartItems.value = emptyList()
                updatePricing(emptyList())
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to place order"
            } finally {
                _isPlacingOrder.value = false
                updateCheckoutState(_cartItems.value.orEmpty())
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearOrderPlacedEvent() {
        _orderPlacedId.value = null
    }

    private fun updatePricing(items: List<CartItem>) {
        val subtotal = items.sumOf { it.lineTotal() }
        _subtotal.value = subtotal
        _deliveryCharge.value = CartPricing.deliveryCharge(subtotal)
        _tax.value = CartPricing.tax(subtotal)
        _total.value = CartPricing.totalAmount(subtotal)
        updateCheckoutState(items)
    }

    private fun updateCheckoutState(items: List<CartItem>) {
        _canPlaceOrder.value =
            items.isNotEmpty() && items.none { it.hasStockIssue() } && _isPlacingOrder.value != true
    }
}
