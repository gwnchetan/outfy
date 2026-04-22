package com.example.outfy.model

import com.google.firebase.Timestamp
import kotlin.math.roundToInt

val ORDER_STATUS_FLOW = listOf(
    "order_placed",
    "departure",
    "arrived_city",
    "out_for_delivery",
    "delivered"
)

data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val size: String = "",
    val quantity: Int = 1,
    val priceAtOrder: Double = 0.0,
    val lineTotal: Double = 0.0
)

data class OrderPricing(
    val subtotal: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val tax: Double = 0.0,
    val totalAmount: Double = 0.0
)

data class OrderPayment(
    val method: String = "cash_on_delivery",
    val label: String = "Cash on Delivery",
    val status: String = "pending"
)

data class OrderStatusEntry(
    val status: String = "",
    val timestamp: Timestamp? = null
)

data class OrderRecord(
    val orderId: String = "",
    val status: String = "order_placed",
    val itemCount: Int = 0,
    val pricing: OrderPricing = OrderPricing(),
    val createdAt: Timestamp? = null,
    val items: List<OrderItem> = emptyList(),
    val payment: OrderPayment = OrderPayment(),
    val shippingAddress: UserAddress? = null,
    val statusHistory: List<OrderStatusEntry> = emptyList()
)

fun OrderRecord.previewText(): String {
    if (items.isEmpty()) return "No items"

    val preview = items.take(2).joinToString(" • ") { item ->
        "${item.name} x${item.quantity}"
    }
    return if (items.size > 2) "$preview • +${items.size - 2} more" else preview
}

fun orderStatusLabel(status: String): String = when (status) {
    "order_placed" -> "Order Placed"
    "departure" -> "Departure"
    "arrived_city" -> "Arrived at Your City"
    "out_for_delivery" -> "Out for Delivery"
    "delivered" -> "Delivered"
    "cancelled" -> "Cancelled"
    else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
}

fun orderStatusDescription(status: String): String = when (status) {
    "order_placed" -> "Your order has been received and packed."
    "departure" -> "The shipment has left the seller hub."
    "arrived_city" -> "The shipment reached your city facility."
    "out_for_delivery" -> "Rider is on the way with your order."
    "delivered" -> "Order delivered successfully."
    "cancelled" -> "This order was cancelled and will not be delivered."
    else -> "Track your order updates here."
}

fun canCancelOrder(status: String): Boolean =
    status in listOf("order_placed", "departure", "arrived_city")

fun canReorderOrder(status: String): Boolean =
    status in listOf("delivered", "cancelled")

fun isClosedOrder(status: String): Boolean =
    status in listOf("delivered", "cancelled")

fun orderProgressIndex(status: String): Int = ORDER_STATUS_FLOW.indexOf(status)

object CartPricing {
    private const val gstRate = 0.05

    fun deliveryCharge(subtotal: Double): Double = if (subtotal <= 0.0) 0.0 else 0.0

    fun tax(subtotal: Double): Double = ((subtotal * gstRate) * 100).roundToInt() / 100.0

    fun totalAmount(subtotal: Double): Double =
        subtotal + deliveryCharge(subtotal) + tax(subtotal)
}
