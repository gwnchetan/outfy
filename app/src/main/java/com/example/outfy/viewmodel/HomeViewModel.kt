package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.ProductRepository
import com.example.outfy.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HomeViewModel : ViewModel() {
    private val repository = ProductRepository()
    private val db = FirebaseFirestore.getInstance()
    private var preferredTarget: String? = null

    private val _products = MutableLiveData<List<Product>>()
    val products: LiveData<List<Product>> = _products

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _searchSuggestions = MutableLiveData<List<String>>(emptyList())
    val searchSuggestions: LiveData<List<String>> = _searchSuggestions

    private val _sectionTitle = MutableLiveData("Recommended for You")
    val sectionTitle: LiveData<String> = _sectionTitle

    private val _sectionMeta = MutableLiveData("Fresh picks from the latest catalog.")
    val sectionMeta: LiveData<String> = _sectionMeta

    private val currentList = mutableListOf<Product>()
    private var isPaginating = false

    fun loadMoreProducts() {
        if (isPaginating) return
        isPaginating = true
        
        viewModelScope.launch {
            val more = repository.getNextPageFromCache()
            if (more.isNotEmpty()) {
                currentList.addAll(more)
                _products.value = ArrayList(currentList)
            }
            isPaginating = false
        }
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.fetchAndCacheProducts()
            _searchSuggestions.value = repository.getSearchSuggestions("")
            resetToDefaultFeed()
            _isLoading.value = false
        }
    }

    fun fetchProductsForUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch and cache ALL products first
                repository.fetchAndCacheProducts()
                
                val userDoc = db.collection("users").document(userId).get().await()
                val gender = userDoc.getString("gender")?.lowercase()

                preferredTarget = when (gender) {
                    "female" -> "women"
                    "male" -> "men"
                    else -> null
                }
                _searchSuggestions.value = repository.getSearchSuggestions("")
                resetToDefaultFeed()
            } catch (e: Exception) {
                _products.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchProducts(query: String) {
        val trimmedQuery = query.trim()
        _searchSuggestions.value = repository.getSearchSuggestions(trimmedQuery)
        val filtered = repository.searchProductsLocal(trimmedQuery)
        currentList.clear()
        currentList.addAll(filtered)
        _products.value = ArrayList(currentList)
        _sectionTitle.value = if (trimmedQuery.isEmpty()) "Search Products" else "Search Results"
        _sectionMeta.value = when {
            trimmedQuery.isEmpty() -> "Type a product name, category, or tag to explore."
            filtered.isEmpty() -> "No matches for \"$trimmedQuery\". Try another keyword."
            else -> "${filtered.size} matches for \"$trimmedQuery\"."
        }
    }

    fun filterByTarget(target: String) {
        _isLoading.value = true
        val filtered = repository.getProductsByTargetLocal(target)
        currentList.clear()
        currentList.addAll(filtered)
        _products.value = ArrayList(currentList)
        _sectionTitle.value = when (target) {
            "men" -> "Men's Section"
            "women" -> "Women's Section"
            "kids" -> "Kids' Section"
            else -> "Catalog"
        }
        _sectionMeta.value = when {
            filtered.isEmpty() -> "No products found in this section yet."
            else -> "${filtered.size} products available in this section."
        }
        _searchSuggestions.value = repository.getSearchSuggestions(target)
        _isLoading.value = false
    }

    fun resetToDefaultFeed() {
        val defaultProducts = when (preferredTarget) {
            "women" -> repository.getProductsByTargetLocal("women")
            "men" -> repository.getProductsByTargetLocal("men")
            else -> repository.getAllCached()
        }

        currentList.clear()
        currentList.addAll(defaultProducts)
        _products.value = ArrayList(currentList)
        _sectionTitle.value = when (preferredTarget) {
            "women" -> "Women's Collection"
            "men" -> "Men's Collection"
            else -> "Recommended for You"
        }
        _sectionMeta.value = when {
            defaultProducts.isEmpty() -> "No products available right now."
            preferredTarget == "women" -> "Every women's product available right now."
            preferredTarget == "men" -> "Every men's product available right now."
            else -> "${defaultProducts.size} products ready to explore."
        }
        _searchSuggestions.value = repository.getSearchSuggestions("")
    }

    fun fetchProducts() {
        refreshProducts()
    }
}
