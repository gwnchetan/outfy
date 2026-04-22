package com.example.outfy.model

data class CartItem(
    val cartItemId: String = "",
    val productId: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val target: String = "",
    val size: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val originalPrice: Double = 0.0,
    val maxQuantity: Int = 0,
    val isAvailable: Boolean = true
)

fun CartItem.lineTotal(): Double = unitPrice * quantity

fun CartItem.hasDiscount(): Boolean = originalPrice > unitPrice

fun CartItem.hasStockIssue(): Boolean = !isAvailable || maxQuantity <= 0 || quantity > maxQuantity

fun CartItem.stockMessage(): String = when {
    !isAvailable -> "This product is no longer available."
    maxQuantity <= 0 -> "This size is out of stock."
    quantity > maxQuantity -> "Only $maxQuantity left for size $size."
    else -> "$maxQuantity available in size $size."
}
