package com.example.outfy.data

import com.example.outfy.model.Product
import com.example.outfy.model.isAvailable
import com.example.outfy.model.toProductOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class WishlistRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun docId(userId: String, productId: String): String = "${userId}_${productId}"

    suspend fun isWishlisted(productId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext false
        if (productId.isBlank()) return@withContext false

        db.collection("wishlists")
            .document(docId(userId, productId))
            .get()
            .await()
            .exists()
    }

    suspend fun toggleWishlist(productId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again.")
        if (productId.isBlank()) throw IllegalStateException("Product is unavailable.")

        val ref = db.collection("wishlists").document(docId(userId, productId))
        val snapshot = ref.get().await()

        if (snapshot.exists()) {
            ref.delete().await()
            false
        } else {
            ref.set(
                mapOf(
                    "userId" to userId,
                    "productId" to productId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            true
        }
    }

    suspend fun getWishlistProducts(): List<Product> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext emptyList()
        val wishlistDocs = db.collection("wishlists")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        if (wishlistDocs.isEmpty()) return@withContext emptyList()

        val productIds = wishlistDocs.mapNotNull { it.getString("productId") }.distinct()
        val orderIndex = productIds.withIndex().associate { it.value to it.index }

        productIds.mapNotNull { productId ->
            db.collection("products")
                .document(productId)
                .get()
                .await()
                .toProductOrNull()
        }
            .filter { it.isAvailable() }
            .sortedBy { orderIndex[it.productId] ?: Int.MAX_VALUE }
    }
}
