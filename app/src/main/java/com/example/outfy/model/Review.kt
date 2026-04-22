package com.example.outfy.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class ReviewRecord(
    val reviewId: String = "",
    val userId: String = "",
    val productId: String = "",
    val userName: String = "",
    val rating: Int = 0,
    val reviewText: String = "",
    val orderId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class ReviewEligibility(
    val canReview: Boolean = false,
    val orderId: String? = null,
    val message: String? = null
)

fun ReviewRecord.displayTimestamp(): Timestamp? = updatedAt ?: createdAt

fun ReviewRecord.toInitials(): String =
    userName.split(" ")
        .mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() }?.take(1) }
        .joinToString("")
        .ifBlank { "OC" }

fun DocumentSnapshot.toReviewOrNull(): ReviewRecord? {
    val payload = data ?: return null
    return ReviewRecord(
        reviewId = id,
        userId = payload["userId"] as? String ?: "",
        productId = payload["productId"] as? String ?: "",
        userName = payload["userName"] as? String ?: "",
        rating = (payload["rating"] as? Number)?.toInt() ?: 0,
        reviewText = payload["reviewText"] as? String ?: "",
        orderId = payload["orderId"] as? String ?: "",
        createdAt = payload["createdAt"] as? Timestamp,
        updatedAt = payload["updatedAt"] as? Timestamp
    ).takeIf { it.userId.isNotBlank() && it.productId.isNotBlank() && it.rating in 1..5 }
}
