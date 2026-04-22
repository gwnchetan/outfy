package com.example.outfy.data

import com.example.outfy.model.ReviewEligibility
import com.example.outfy.model.ReviewRecord
import com.example.outfy.model.displayTimestamp
import com.example.outfy.model.toReviewOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ReviewRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun currentUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again.")

    private fun reviewDocId(userId: String, productId: String): String = "${userId}_${productId}"

    suspend fun getReviewsForProduct(productId: String): List<ReviewRecord> = withContext(Dispatchers.IO) {
        if (productId.isBlank()) return@withContext emptyList()

        db.collection("reviews")
            .whereEqualTo("productId", productId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toReviewOrNull() }
            .sortedByDescending { it.displayTimestamp()?.seconds ?: 0L }
    }

    suspend fun getUserReview(productId: String): ReviewRecord? = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext null
        if (productId.isBlank()) return@withContext null

        db.collection("reviews")
            .document(reviewDocId(userId, productId))
            .get()
            .await()
            .takeIf { it.exists() }
            ?.toReviewOrNull()
    }

    suspend fun getReviewedProductIds(productIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        if (productIds.isEmpty()) return@withContext emptySet()

        productIds
            .distinct()
            .mapNotNull { productId ->
                getUserReview(productId)?.productId
            }
            .toSet()
    }

    suspend fun canUserReview(productId: String): ReviewEligibility = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid
            ?: return@withContext ReviewEligibility(
                canReview = false,
                message = "Sign in again to review this product."
            )

        if (productId.isBlank()) {
            return@withContext ReviewEligibility(
                canReview = false,
                message = "Product is unavailable for review."
            )
        }

        val existingReview = getUserReview(productId)
        if (existingReview != null) {
            return@withContext ReviewEligibility(
                canReview = true,
                orderId = existingReview.orderId,
                message = "You can update your review anytime."
            )
        }

        val orderId = findDeliveredOrderId(userId, productId)
        if (orderId != null) {
            ReviewEligibility(
                canReview = true,
                orderId = orderId,
                message = "You have received this product. Share your experience."
            )
        } else {
            ReviewEligibility(
                canReview = false,
                message = "Review submission becomes available after delivery."
            )
        }
    }

    suspend fun submitReview(productId: String, rating: Int, reviewText: String): String =
        withContext(Dispatchers.IO) {
            val userId = currentUserId()
            val normalizedText = reviewText.trim()

            if (productId.isBlank()) {
                throw IllegalStateException("Product is unavailable for review.")
            }
            if (rating !in 1..5) {
                throw IllegalStateException("Please choose a rating between 1 and 5.")
            }
            if (normalizedText.isBlank()) {
                throw IllegalStateException("Write a short review before submitting.")
            }

            val existingReview = getUserReview(productId)
            val eligibility = canUserReview(productId)

            if (existingReview == null && !eligibility.canReview) {
                throw IllegalStateException(eligibility.message ?: "You cannot review this product yet.")
            }

            val userName = loadCurrentUserName(userId)
            val orderId = existingReview?.orderId ?: eligibility.orderId
            if (orderId.isNullOrBlank()) {
                throw IllegalStateException("Delivered order proof was not found for this review.")
            }

            val reviewRef = db.collection("reviews").document(reviewDocId(userId, productId))
            val payload = mutableMapOf<String, Any>(
                "userId" to userId,
                "productId" to productId,
                "userName" to userName,
                "rating" to rating,
                "reviewText" to normalizedText,
                "orderId" to orderId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existingReview == null) {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }

            reviewRef.set(payload, SetOptions.merge()).await()
            refreshProductAggregates(productId)

            if (existingReview == null) "Review submitted" else "Review updated"
        }

    private suspend fun loadCurrentUserName(userId: String): String {
        val userDoc = db.collection("users").document(userId).get().await()
        val profileName = userDoc.getString("name").orEmpty().trim()
        if (profileName.isNotEmpty()) return profileName

        val authUser = auth.currentUser
        val authName = authUser?.displayName.orEmpty().trim()
        if (authName.isNotEmpty()) return authName

        val emailName = authUser?.email?.substringBefore("@").orEmpty().trim()
        if (emailName.isNotEmpty()) return emailName

        return "Outfy Customer"
    }

    private suspend fun findDeliveredOrderId(userId: String, productId: String): String? {
        val orders = db.collection("orders")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        return orders
            .filter { it.getString("status") == "delivered" }
            .filter { doc ->
                val items = doc.get("items") as? List<*>
                items.orEmpty().any { rawItem ->
                    val item = rawItem as? Map<*, *> ?: return@any false
                    (item["productId"] as? String).orEmpty() == productId
                }
            }
            .sortedByDescending { it.getTimestamp("createdAt")?.seconds ?: 0L }
            .firstOrNull()
            ?.id
    }

    private suspend fun refreshProductAggregates(productId: String) {
        val reviews = getReviewsForProduct(productId)
        val ratingCount = reviews.size
        val ratingAverage = if (ratingCount == 0) {
            0.0
        } else {
            ((reviews.sumOf { it.rating }.toDouble() / ratingCount) * 10).roundToInt() / 10.0
        }

        db.collection("products")
            .document(productId)
            .set(
                mapOf(
                    "ratingCount" to ratingCount,
                    "ratingAverage" to ratingAverage
                ),
                SetOptions.merge()
            )
            .await()
    }
}
