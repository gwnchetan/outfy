package com.example.outfy.data

import com.example.outfy.model.Product
import com.example.outfy.model.hasDiscount
import com.example.outfy.model.isAvailable
import com.example.outfy.model.isInStock
import com.example.outfy.model.toProductOrNull
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProductRepository {
    private val db = FirebaseFirestore.getInstance()
    private var cachedProducts: List<Product> = emptyList()
    private var paginationIndex = 0

    // Fetch all active products once and cache them
    suspend fun fetchAndCacheProducts(): List<Product> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("products")
                .get()
                .await()
            cachedProducts = snapshot.documents
                .mapNotNull { it.toProductOrNull() }
                .filter { it.isAvailable() }
                .sortedWith(
                    compareByDescending<Product> { it.isInStock() }
                        .thenBy { it.name.lowercase() }
                )
            paginationIndex = 0
            cachedProducts
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Get a page of 10 products from cache
    fun getNextPageFromCache(): List<Product> {
        if (cachedProducts.isEmpty()) return emptyList()
        
        val end = minOf(paginationIndex + 10, cachedProducts.size)
        val page = cachedProducts.subList(paginationIndex, end).toList()
        
        paginationIndex = if (end >= cachedProducts.size) 0 else end // loop back
        return page
    }

    // Filter from cache to avoid Firestore Index issues
    fun getProductsByTargetLocal(target: String): List<Product> {
        val targetLower = target.lowercase()
        return cachedProducts.filter {
            it.target.trim().lowercase() == targetLower
        }
    }

    fun getProductsForTarget(target: String?): List<Product> {
        return if (target.isNullOrBlank()) {
            cachedProducts
        } else {
            getProductsByTargetLocal(target)
        }
    }

    fun searchProductsLocal(query: String): List<Product> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return cachedProducts

        return cachedProducts
            .filter { product ->
                product.name.contains(normalizedQuery, ignoreCase = true) ||
                    product.category.contains(normalizedQuery, ignoreCase = true) ||
                    product.target.contains(normalizedQuery, ignoreCase = true) ||
                    product.tags.any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            .sortedWith(
                compareByDescending<Product> { it.name.startsWith(normalizedQuery, ignoreCase = true) }
                    .thenBy { it.name.lowercase() }
            )
    }

    fun getSearchSuggestions(query: String): List<String> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return emptyList() // ← was dumping everything before

        val suggestions = linkedSetOf<String>()
        cachedProducts.forEach { product ->
            // Don't add full product names — only short keywords
            if (product.category.contains(normalizedQuery, ignoreCase = true))
                suggestions += product.category.trim()
            product.tags.forEach { tag ->
                if (tag.contains(normalizedQuery, ignoreCase = true))
                    suggestions += tag.trim()
            }
        }
        return suggestions.take(6).toList()
    }

    fun getBestSellerProducts(limit: Int = 10, target: String? = null, saleOnly: Boolean = false): List<Product> {
        val source = getProductsForTarget(target)
            .filter { it.isInStock() }
            .let { products ->
                if (saleOnly) products.filter { it.hasDiscount() } else products
            }

        return source.sortedWith(
            compareByDescending<Product> {
                if (it.hasDiscount() && it.originalPrice > 0) {
                    (it.originalPrice - it.price) / it.originalPrice
                } else {
                    0.0
                }
            }.thenBy { it.name.lowercase() }
        ).take(limit)
    }

    fun getFeaturedProducts(limit: Int = 12, target: String? = null, saleOnly: Boolean = false): List<Product> {
        val source = getProductsForTarget(target)
            .let { products ->
                if (saleOnly) products.filter { it.hasDiscount() } else products
            }

        return source.take(limit)
    }

    fun getAllCached(): List<Product> = cachedProducts
}
