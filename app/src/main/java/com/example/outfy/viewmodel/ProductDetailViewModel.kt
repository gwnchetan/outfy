package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.CartRepository
import com.example.outfy.data.ReviewRepository
import com.example.outfy.data.WishlistRepository
import com.example.outfy.model.Product
import com.example.outfy.model.ReviewRecord
import com.example.outfy.model.isAvailable
import com.example.outfy.model.toProductOrNull
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProductDetailViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val cartRepository = CartRepository()
    private val wishlistRepository = WishlistRepository()
    private val reviewRepository = ReviewRepository()

    private val _product = MutableLiveData<Product?>()
    val product: LiveData<Product?> = _product

    private val _similarProducts = MutableLiveData<List<Product>>()
    val similarProducts: LiveData<List<Product>> = _similarProducts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isWishlisted = MutableLiveData(false)
    val isWishlisted: LiveData<Boolean> = _isWishlisted

    private val _wishlistMessage = MutableLiveData<String?>()
    val wishlistMessage: LiveData<String?> = _wishlistMessage

    private val _isWishlistUpdating = MutableLiveData(false)
    val isWishlistUpdating: LiveData<Boolean> = _isWishlistUpdating

    private val _cartMessage = MutableLiveData<String?>()
    val cartMessage: LiveData<String?> = _cartMessage

    private val _isAddingToCart = MutableLiveData(false)
    val isAddingToCart: LiveData<Boolean> = _isAddingToCart

    private val _reviews = MutableLiveData<List<ReviewRecord>>(emptyList())
    val reviews: LiveData<List<ReviewRecord>> = _reviews

    private val _userReview = MutableLiveData<ReviewRecord?>()
    val userReview: LiveData<ReviewRecord?> = _userReview

    private val _canReview = MutableLiveData(false)
    val canReview: LiveData<Boolean> = _canReview

    private val _reviewHintMessage = MutableLiveData<String?>()
    val reviewHintMessage: LiveData<String?> = _reviewHintMessage

    private val _isReviewsLoading = MutableLiveData(false)
    val isReviewsLoading: LiveData<Boolean> = _isReviewsLoading

    private val _isSubmittingReview = MutableLiveData(false)
    val isSubmittingReview: LiveData<Boolean> = _isSubmittingReview

    private val _reviewMessage = MutableLiveData<String?>()
    val reviewMessage: LiveData<String?> = _reviewMessage

    fun fetchProductDetails(productId: String) {
        if (productId.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val doc = db.collection("products").document(productId).get().await()
                if (doc.exists()) {
                    val productData = doc.toProductOrNull()
                    _product.value = productData
                    
                    productData?.let {
                        fetchSimilarProducts(it.category, it.productId)
                    }
                } else {
                    _product.value = null
                }
            } catch (e: Exception) {
                _product.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun fetchSimilarProducts(category: String, currentProductId: String) {
        if (category.isBlank()) {
            _similarProducts.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val snapshot = db.collection("products")
                    .whereEqualTo("category", category)
                    .limit(11)
                    .get()
                    .await()
                
                val list = snapshot.documents
                    .mapNotNull { it.toProductOrNull() }
                    .filter { it.isAvailable() }
                    .filter { it.productId != currentProductId }
                    .take(10)
                
                _similarProducts.value = list
            } catch (e: Exception) {
                _similarProducts.value = emptyList()
            }
        }
    }

    fun loadWishlistState(productId: String) {
        if (productId.isBlank()) return

        viewModelScope.launch {
            _isWishlisted.value = wishlistRepository.isWishlisted(productId)
        }
    }

    fun loadReviewState(productId: String) {
        if (productId.isBlank()) return

        viewModelScope.launch {
            _isReviewsLoading.value = true
            try {
                _reviews.value = reviewRepository.getReviewsForProduct(productId)
                _userReview.value = reviewRepository.getUserReview(productId)
                val eligibility = reviewRepository.canUserReview(productId)
                _canReview.value = eligibility.canReview
                _reviewHintMessage.value = eligibility.message
            } catch (e: Exception) {
                _reviews.value = emptyList()
                _userReview.value = null
                _canReview.value = false
                _reviewHintMessage.value = e.message ?: "Unable to load reviews"
            } finally {
                _isReviewsLoading.value = false
            }
        }
    }

    fun toggleWishlist(productId: String) {
        if (productId.isBlank() || _isWishlistUpdating.value == true) return

        viewModelScope.launch {
            _isWishlistUpdating.value = true
            try {
                val wishlisted = wishlistRepository.toggleWishlist(productId)
                _isWishlisted.value = wishlisted
                _wishlistMessage.value =
                    if (wishlisted) "Added to wishlist" else "Removed from wishlist"
            } catch (e: Exception) {
                _wishlistMessage.value = e.message ?: "Wishlist update failed"
            } finally {
                _isWishlistUpdating.value = false
            }
        }
    }

    fun clearWishlistMessage() {
        _wishlistMessage.value = null
    }

    fun addToCart(selectedSize: String?) {
        val productId = _product.value?.productId.orEmpty()
        if (productId.isBlank()) {
            _cartMessage.value = "Product is unavailable."
            return
        }
        if (selectedSize.isNullOrBlank() || _isAddingToCart.value == true) {
            return
        }

        viewModelScope.launch {
            _isAddingToCart.value = true
            try {
                _cartMessage.value = cartRepository.addToCart(productId, selectedSize)
            } catch (e: Exception) {
                _cartMessage.value = e.message ?: "Unable to add to cart"
            } finally {
                _isAddingToCart.value = false
            }
        }
    }

    fun clearCartMessage() {
        _cartMessage.value = null
    }

    fun submitReview(rating: Int, reviewText: String) {
        val productId = _product.value?.productId.orEmpty()
        if (productId.isBlank() || _isSubmittingReview.value == true) return

        viewModelScope.launch {
            _isSubmittingReview.value = true
            try {
                _reviewMessage.value = reviewRepository.submitReview(productId, rating, reviewText)
                fetchProductDetails(productId)
                loadReviewState(productId)
            } catch (e: Exception) {
                _reviewMessage.value = e.message ?: "Unable to submit review"
            } finally {
                _isSubmittingReview.value = false
            }
        }
    }

    fun clearReviewMessage() {
        _reviewMessage.value = null
    }
}
