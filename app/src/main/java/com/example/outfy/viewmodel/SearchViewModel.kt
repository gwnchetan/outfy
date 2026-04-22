package com.example.outfy.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.outfy.data.ProductRepository
import com.example.outfy.model.Product
import com.example.outfy.model.hasDiscount
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val repository = ProductRepository()
    private var currentQuery = ""
    private var selectedTarget: String? = null
    private var saleOnly = false

    private val _results = MutableLiveData<List<Product>>(emptyList())
    val results: LiveData<List<Product>> = _results

    private val _bestSellers = MutableLiveData<List<Product>>(emptyList())
    val bestSellers: LiveData<List<Product>> = _bestSellers

    private val _resultTitle = MutableLiveData("Top Results")
    val resultTitle: LiveData<String> = _resultTitle

    private val _resultMeta = MutableLiveData("Fresh picks, best sellers, and curated suggestions.")
    val resultMeta: LiveData<String> = _resultMeta

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadCatalog() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.fetchAndCacheProducts()
            applySearch()
            _isLoading.value = false
        }
    }

    fun updateQuery(query: String) {
        currentQuery = query.trim()
        applySearch()
    }

    fun setTargetFilter(target: String?) {
        selectedTarget = target
        applySearch()
    }

    fun setSaleOnly(enabled: Boolean) {
        saleOnly = enabled
        applySearch()
    }

    fun isCurrentTarget(target: String?): Boolean = selectedTarget == target

    fun isSaleOnly(): Boolean = saleOnly

    private fun applySearch() {
        val filteredByTarget = repository.getProductsForTarget(selectedTarget)
        val targetFiltered = if (saleOnly) {
            filteredByTarget.filter { it.hasDiscount() }
        } else {
            filteredByTarget
        }

        val topResults = if (currentQuery.isBlank()) {
            repository.getFeaturedProducts(limit = 12, target = selectedTarget, saleOnly = saleOnly)
        } else {
            repository.searchProductsLocal(currentQuery)
                .filter { product ->
                    (selectedTarget == null || product.target.trim().equals(selectedTarget, ignoreCase = true)) &&
                        (!saleOnly || product.hasDiscount())
                }
        }

        _results.value = topResults
        _bestSellers.value = repository.getBestSellerProducts(limit = 8, target = selectedTarget, saleOnly = saleOnly)
        _resultTitle.value = if (currentQuery.isBlank()) "Top Results" else "Results for \"$currentQuery\""
        _resultMeta.value = when {
            currentQuery.isBlank() && saleOnly -> "Showing sale picks with fast filters and best sellers."
            currentQuery.isBlank() && selectedTarget != null ->
                "Browsing ${selectedTarget!!.replaceFirstChar { it.uppercase() }} with curated suggestions."
            currentQuery.isBlank() -> "Fresh picks, best sellers, and curated suggestions."
            topResults.isEmpty() -> "No products matched your search. Try another keyword or filter."
            else -> "${topResults.size} products matched your search."
        }
    }
}
