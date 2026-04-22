package com.example.outfy.data

import com.example.outfy.model.CartItem
import com.example.outfy.model.displayOriginalPrice
import com.example.outfy.model.imageGallery
import com.example.outfy.model.isAvailable
import com.example.outfy.model.stockForSize
import com.example.outfy.model.toProductOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CartRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun currentUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again.")

    private fun sanitizeSize(size: String): String =
        size.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")

    private fun docId(userId: String, productId: String, size: String): String =
        "${userId}_${productId}_${sanitizeSize(size)}"

    suspend fun addToCart(productId: String, size: String, quantity: Int = 1): String =
        withContext(Dispatchers.IO) {
            val userId = currentUserId()
            if (productId.isBlank()) throw IllegalStateException("Product is unavailable.")
            if (size.isBlank()) throw IllegalStateException("Select a size before adding to cart.")

            val productRef = db.collection("products").document(productId)
            val cartRef = db.collection("cartItems").document(docId(userId, productId, size))

            db.runTransaction { transaction ->
                val productSnapshot = transaction.get(productRef)
                val product = productSnapshot.toProductOrNull()
                    ?: throw IllegalStateException("Product details are unavailable.")

                if (!product.isAvailable()) {
                    throw IllegalStateException("This product is no longer available.")
                }

                val availableStock = product.stockForSize(size)
                if (availableStock <= 0) {
                    throw IllegalStateException("Size $size is out of stock.")
                }

                val cartSnapshot = transaction.get(cartRef)
                val existingQuantity = cartSnapshot.getLong("quantity")?.toInt() ?: 0
                val nextQuantity = (existingQuantity + quantity).coerceAtMost(availableStock)

                if (existingQuantity >= availableStock) {
                    throw IllegalStateException("Only $availableStock left for size $size.")
                }

                transaction.set(
                    cartRef,
                    mapOf(
                        "userId" to userId,
                        "productId" to product.productId,
                        "productName" to product.name,
                        "imageUrl" to product.imageGallery().firstOrNull().orEmpty(),
                        "category" to product.category,
                        "target" to product.target,
                        "size" to size.trim(),
                        "quantity" to nextQuantity,
                        "unitPrice" to product.price,
                        "originalPrice" to product.displayOriginalPrice(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (existingQuantity == 0) {
                    "Added to cart"
                } else {
                    "Cart updated to $nextQuantity"
                }
            }.await()
        }

    suspend fun getCartItems(): List<CartItem> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext emptyList()
        val cartDocs = db.collection("cartItems")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        if (cartDocs.isEmpty()) return@withContext emptyList()

        val productsById = mutableMapOf<String, com.example.outfy.model.Product?>()
        cartDocs
            .mapNotNull { it.getString("productId") }
            .distinct()
            .forEach { productId ->
                productsById[productId] =
                    db.collection("products").document(productId).get().await().toProductOrNull()
            }

        cartDocs
            .sortedByDescending { it.getTimestamp("updatedAt")?.seconds ?: 0L }
            .map { doc ->
                val productId = doc.getString("productId").orEmpty()
                val size = doc.getString("size").orEmpty()
                val liveProduct = productsById[productId]

                CartItem(
                    cartItemId = doc.id,
                    productId = productId,
                    name = liveProduct?.name?.ifBlank { doc.getString("productName").orEmpty() }
                        ?: doc.getString("productName").orEmpty(),
                    imageUrl = liveProduct?.imageGallery()?.firstOrNull().orEmpty()
                        .ifBlank { doc.getString("imageUrl").orEmpty() },
                    category = liveProduct?.category?.ifBlank { doc.getString("category").orEmpty() }
                        ?: doc.getString("category").orEmpty(),
                    target = liveProduct?.target?.ifBlank { doc.getString("target").orEmpty() }
                        ?: doc.getString("target").orEmpty(),
                    size = size,
                    quantity = doc.getLong("quantity")?.toInt() ?: 1,
                    unitPrice = liveProduct?.price ?: (doc.getDouble("unitPrice") ?: 0.0),
                    originalPrice = liveProduct?.displayOriginalPrice()
                        ?: (doc.getDouble("originalPrice") ?: doc.getDouble("unitPrice") ?: 0.0),
                    maxQuantity = liveProduct?.stockForSize(size) ?: 0,
                    isAvailable = liveProduct?.isAvailable() ?: false
                )
            }
    }

    suspend fun updateQuantity(cartItemId: String, quantity: Int) = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        if (cartItemId.isBlank()) throw IllegalStateException("Cart item is missing.")

        val cartRef = db.collection("cartItems").document(cartItemId)
        if (quantity <= 0) {
            val snapshot = cartRef.get().await()
            if (snapshot.getString("userId") == userId) {
                cartRef.delete().await()
            }
            return@withContext
        }

        db.runTransaction { transaction ->
            val cartSnapshot = transaction.get(cartRef)
            if (!cartSnapshot.exists()) {
                throw IllegalStateException("Cart item no longer exists.")
            }

            if (cartSnapshot.getString("userId") != userId) {
                throw IllegalStateException("Cart item does not belong to this account.")
            }

            val productId = cartSnapshot.getString("productId").orEmpty()
            val size = cartSnapshot.getString("size").orEmpty()
            val productSnapshot = transaction.get(db.collection("products").document(productId))
            val product = productSnapshot.toProductOrNull()
                ?: throw IllegalStateException("Product details are unavailable.")

            val availableStock = product.stockForSize(size)
            if (!product.isAvailable() || availableStock <= 0) {
                throw IllegalStateException("This cart item is no longer available.")
            }

            if (quantity > availableStock) {
                throw IllegalStateException("Only $availableStock left for size $size.")
            }

            transaction.update(
                cartRef,
                mapOf(
                    "quantity" to quantity,
                    "unitPrice" to product.price,
                    "originalPrice" to product.displayOriginalPrice(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }

    suspend fun removeItem(cartItemId: String) = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        if (cartItemId.isBlank()) return@withContext

        val ref = db.collection("cartItems").document(cartItemId)
        val snapshot = ref.get().await()
        if (snapshot.getString("userId") == userId) {
            ref.delete().await()
        }
    }

    suspend fun getCartCount(): Int = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext 0
        val count = db.collection("cartItems")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .sumOf { it.getLong("quantity")?.toInt() ?: 0 }
        count
    }
}
