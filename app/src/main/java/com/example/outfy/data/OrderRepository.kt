package com.example.outfy.data

import com.example.outfy.model.CartPricing
import com.example.outfy.model.OrderItem
import com.example.outfy.model.OrderPayment
import com.example.outfy.model.OrderPricing
import com.example.outfy.model.OrderRecord
import com.example.outfy.model.OrderStatusEntry
import com.example.outfy.model.UserAddress
import com.example.outfy.model.availableSizes
import com.example.outfy.model.canCancelOrder
import com.example.outfy.model.displayOriginalPrice
import com.example.outfy.model.imageGallery
import com.example.outfy.model.isComplete
import com.example.outfy.model.isAvailable
import com.example.outfy.model.stockForSize
import com.example.outfy.model.toProductOrNull
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OrderRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val cartRepository = CartRepository()

    private data class PendingCartOrderItem(
        val cartRef: com.google.firebase.firestore.DocumentReference,
        val productRef: com.google.firebase.firestore.DocumentReference,
        val productId: String,
        val size: String,
        val quantity: Int,
        val productName: String,
        val imageUrl: String,
        val unitPrice: Double,
        val originalPrice: Double,
        val lineTotal: Double
    )

    private fun currentUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in again.")

    private fun parseOrderSnapshot(doc: DocumentSnapshot): OrderRecord {
        val items = (doc.get("items") as? List<*>)
            ?.mapNotNull { raw ->
                val data = raw as? Map<*, *> ?: return@mapNotNull null
                OrderItem(
                    productId = data["productId"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    imageUrl = data["imageUrl"] as? String ?: "",
                    size = data["size"] as? String ?: "",
                    quantity = (data["quantity"] as? Number)?.toInt() ?: 0,
                    priceAtOrder = ((data["priceAtOrder"] ?: data["unitPrice"]) as? Number)?.toDouble() ?: 0.0,
                    lineTotal = (data["lineTotal"] as? Number)?.toDouble()
                        ?: (((data["priceAtOrder"] ?: data["unitPrice"]) as? Number)?.toDouble() ?: 0.0) *
                        ((data["quantity"] as? Number)?.toInt() ?: 0)
                )
            }
            .orEmpty()

        val pricingMap = doc.get("pricing") as? Map<*, *>
        val paymentMap = doc.get("payment") as? Map<*, *>
        val addressMap = doc.get("shippingAddress") as? Map<*, *>
        val history = (doc.get("statusHistory") as? List<*>)
            ?.mapNotNull { raw ->
                val data = raw as? Map<*, *> ?: return@mapNotNull null
                OrderStatusEntry(
                    status = data["status"] as? String ?: "",
                    timestamp = data["timestamp"] as? Timestamp
                )
            }
            .orEmpty()

        return OrderRecord(
            orderId = doc.id,
            status = doc.getString("status").orEmpty().ifBlank { "order_placed" },
            itemCount = doc.getLong("itemCount")?.toInt() ?: items.sumOf { it.quantity },
            pricing = OrderPricing(
                subtotal = (pricingMap?.get("subtotal") as? Number)?.toDouble()
                    ?: (doc.getDouble("subtotal") ?: 0.0),
                deliveryCharge = (pricingMap?.get("deliveryCharge") as? Number)?.toDouble()
                    ?: (doc.getDouble("shippingFee") ?: 0.0),
                tax = (pricingMap?.get("tax") as? Number)?.toDouble() ?: 0.0,
                totalAmount = (pricingMap?.get("totalAmount") as? Number)?.toDouble()
                    ?: (doc.getDouble("total") ?: 0.0)
            ),
            createdAt = doc.getTimestamp("createdAt") ?: doc.getTimestamp("placedAt"),
            items = items,
            payment = OrderPayment(
                method = paymentMap?.get("method") as? String ?: "cash_on_delivery",
                label = paymentMap?.get("label") as? String ?: "Cash on Delivery",
                status = paymentMap?.get("status") as? String ?: "pending"
            ),
            shippingAddress = addressMap?.let { address ->
                UserAddress(
                    fullName = address["fullName"] as? String ?: "",
                    phone = address["phone"] as? String ?: "",
                    line1 = address["line1"] as? String ?: "",
                    line2 = address["line2"] as? String ?: "",
                    city = address["city"] as? String ?: "",
                    state = address["state"] as? String ?: "",
                    pincode = address["pincode"] as? String ?: "",
                    label = address["label"] as? String ?: "HOME"
                )
            },
            statusHistory = history
        )
    }

    suspend fun placeOrder(address: UserAddress): String = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        if (!address.isComplete()) {
            throw IllegalStateException("Add a complete delivery address before placing the order.")
        }

        val cartDocs = db.collection("cartItems")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents

        if (cartDocs.isEmpty()) {
            throw IllegalStateException("Your cart is empty.")
        }

        val userRef = db.collection("users").document(userId)
        val userSnapshot = userRef.get().await()
        val orderRef = db.collection("orders").document()

        db.runTransaction { transaction ->
            val freshCarts = cartDocs.map { cartDoc ->
                transaction.get(cartDoc.reference).also { freshCart ->
                    if (!freshCart.exists()) {
                        throw IllegalStateException("Your cart changed. Please review it again.")
                    }
                }
            }

            val pendingItems = mutableListOf<PendingCartOrderItem>()
            val productRefs = linkedMapOf<String, com.google.firebase.firestore.DocumentReference>()

            freshCarts.forEach { freshCart ->
                val productId = freshCart.getString("productId").orEmpty()
                val size = freshCart.getString("size").orEmpty()
                val quantity = freshCart.getLong("quantity")?.toInt() ?: 0

                if (productId.isBlank() || size.isBlank() || quantity <= 0) {
                    throw IllegalStateException("One cart item is incomplete.")
                }

                val productRef = db.collection("products").document(productId)
                productRefs[productId] = productRef

                pendingItems += PendingCartOrderItem(
                    cartRef = freshCart.reference,
                    productRef = productRef,
                    productId = productId,
                    size = size,
                    quantity = quantity,
                    productName = "",
                    imageUrl = "",
                    unitPrice = 0.0,
                    originalPrice = 0.0,
                    lineTotal = 0.0
                )
            }

            val productsById = productRefs.mapValues { (_, productRef) ->
                val snapshot = transaction.get(productRef)
                snapshot.toProductOrNull()
                    ?: throw IllegalStateException("A product in your cart is unavailable.")
            }

            val finalizedItems = pendingItems.map { item ->
                val product = productsById[item.productId]
                    ?: throw IllegalStateException("A product in your cart is unavailable.")

                if (!product.isAvailable()) {
                    throw IllegalStateException("${product.name} is no longer available.")
                }

                val availableStock = product.stockForSize(item.size)
                if (availableStock < item.quantity) {
                    throw IllegalStateException("Only $availableStock left for ${product.name} in size ${item.size}.")
                }

                val unitPrice = product.price
                val lineTotal = unitPrice * item.quantity
                item.copy(
                    productName = product.name,
                    imageUrl = product.imageGallery().firstOrNull().orEmpty(),
                    unitPrice = unitPrice,
                    originalPrice = product.displayOriginalPrice(),
                    lineTotal = lineTotal
                )
            }

            val updatedSizesByProduct = finalizedItems
                .groupBy { it.productId }
                .mapValues { (productId, itemsForProduct) ->
                    val product = productsById[productId]
                        ?: throw IllegalStateException("A product in your cart is unavailable.")
                    val remainingStockBySize: MutableMap<String, Int> = product.availableSizes()
                        .associate { size -> size.label to size.stock }
                        .toMutableMap()

                    itemsForProduct.forEach { item ->
                        val currentStock = remainingStockBySize.entries
                            .firstOrNull { it.key.equals(item.size, ignoreCase = true) }
                            ?.value
                            ?: throw IllegalStateException("${product.name} size ${item.size} is unavailable.")

                        val nextStock = currentStock - item.quantity
                        if (nextStock < 0) {
                            throw IllegalStateException("Only $currentStock left for ${product.name} in size ${item.size}.")
                        }

                        val canonicalSizeLabel = remainingStockBySize.keys
                            .first { it.equals(item.size, ignoreCase = true) }
                        remainingStockBySize[canonicalSizeLabel] = nextStock
                    }

                    product.sizes.map { sizeMap ->
                        val label = ((sizeMap["label"] ?: sizeMap["size"]) as? String)?.trim().orEmpty()
                        if (label.isBlank()) {
                            sizeMap
                        } else {
                            sizeMap.toMutableMap().apply {
                                val updatedStock = remainingStockBySize.entries
                                    .firstOrNull { it.key.equals(label, ignoreCase = true) }
                                    ?.value
                                if (updatedStock != null) {
                                    put("stock", updatedStock)
                                }
                            }
                        }
                    }
                }

            val orderItems = finalizedItems.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "name" to item.productName,
                    "imageUrl" to item.imageUrl,
                    "size" to item.size,
                    "quantity" to item.quantity,
                    "priceAtOrder" to item.unitPrice,
                    "originalPrice" to item.originalPrice,
                    "lineTotal" to item.lineTotal
                )
            }
            val subtotal = finalizedItems.sumOf { it.lineTotal }

            val deliveryCharge = CartPricing.deliveryCharge(subtotal)
            val tax = CartPricing.tax(subtotal)
            val customerName = userSnapshot.getString("name").orEmpty()
            val customerEmail = userSnapshot.getString("email").orEmpty()
            val customerPhone = userSnapshot.getString("phone").orEmpty()
            val pricing = mapOf(
                "subtotal" to subtotal,
                "deliveryCharge" to deliveryCharge,
                "tax" to tax,
                "totalAmount" to CartPricing.totalAmount(subtotal)
            )

            val shippingAddress = mapOf(
                "fullName" to address.fullName.trim(),
                "phone" to address.phone.trim(),
                "line1" to address.line1.trim(),
                "line2" to address.line2.trim(),
                "city" to address.city.trim(),
                "state" to address.state.trim(),
                "pincode" to address.pincode.trim(),
                "label" to address.label.trim()
            )

            updatedSizesByProduct.forEach { (productId, updatedSizes) ->
                val productRef = productRefs[productId]
                    ?: throw IllegalStateException("A product in your cart is unavailable.")
                transaction.update(productRef, "sizes", updatedSizes)
            }

            finalizedItems.forEach { item ->
                transaction.delete(item.cartRef)
            }

            transaction.set(userRef, mapOf("defaultAddress" to shippingAddress), SetOptions.merge())
            transaction.set(
                orderRef,
                mapOf(
                    "userId" to userId,
                    "status" to "order_placed",
                    "itemCount" to orderItems.sumOf { it["quantity"] as Int },
                    "items" to orderItems,
                    "pricing" to pricing,
                    "shippingAddress" to shippingAddress,
                    "payment" to mapOf(
                        "method" to "cash_on_delivery",
                        "label" to "Cash on Delivery",
                        "status" to "pending"
                    ),
                    "userSnapshot" to mapOf(
                        "uid" to userId,
                        "name" to customerName,
                        "email" to customerEmail,
                        "phone" to customerPhone
                    ),
                    "statusHistory" to listOf(
                        mapOf(
                            "status" to "order_placed",
                            "timestamp" to Timestamp.now()
                        )
                    ),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()

        orderRef.id
    }

    suspend fun getOrderById(orderId: String): OrderRecord? = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext null
        if (orderId.isBlank()) return@withContext null

        val snapshot = db.collection("orders").document(orderId).get().await()
        if (!snapshot.exists()) return@withContext null
        if (snapshot.getString("userId") != userId) return@withContext null

        parseOrderSnapshot(snapshot)
    }

    suspend fun cancelOrder(orderId: String): String = withContext(Dispatchers.IO) {
        val userId = currentUserId()
        if (orderId.isBlank()) throw IllegalStateException("Order is unavailable.")

        val orderRef = db.collection("orders").document(orderId)

        db.runTransaction { transaction ->
            val orderSnapshot = transaction.get(orderRef)
            if (!orderSnapshot.exists()) {
                throw IllegalStateException("Order not found.")
            }
            if (orderSnapshot.getString("userId") != userId) {
                throw IllegalStateException("This order does not belong to your account.")
            }

            val order = parseOrderSnapshot(orderSnapshot)
            if (!canCancelOrder(order.status)) {
                throw IllegalStateException("This order can no longer be cancelled.")
            }

            val productRefs = order.items
                .map { item -> item.productId to db.collection("products").document(item.productId) }
                .toMap()

            val productsById = productRefs.mapValues { (_, productRef) ->
                transaction.get(productRef).toProductOrNull()
            }

            val restoredSizesByProduct = order.items
                .groupBy { it.productId }
                .mapValues { (productId, itemsForProduct) ->
                    val product = productsById[productId]
                    if (product == null) {
                        emptyList<Map<String, Any?>>()
                    } else {
                        val restoredStockBySize: MutableMap<String, Int> = product.availableSizes()
                            .associate { size -> size.label to size.stock }
                            .toMutableMap()

                        itemsForProduct.forEach { item ->
                            val canonicalSizeLabel = restoredStockBySize.keys
                                .firstOrNull { it.equals(item.size, ignoreCase = true) }
                                ?: return@forEach
                            val currentStock = restoredStockBySize[canonicalSizeLabel] ?: 0
                            restoredStockBySize[canonicalSizeLabel] = currentStock + item.quantity
                        }

                        product.sizes.map { sizeMap ->
                            val label = ((sizeMap["label"] ?: sizeMap["size"]) as? String)?.trim().orEmpty()
                            if (label.isBlank()) {
                                sizeMap
                            } else {
                                sizeMap.toMutableMap().apply {
                                    val updatedStock = restoredStockBySize.entries
                                        .firstOrNull { it.key.equals(label, ignoreCase = true) }
                                        ?.value
                                    if (updatedStock != null) {
                                        put("stock", updatedStock)
                                    }
                                }
                            }
                        }
                    }
                }

            val currentHistory = (orderSnapshot.get("statusHistory") as? List<*>)
                ?.mapNotNull { entry ->
                    val data = entry as? Map<*, *> ?: return@mapNotNull null
                    data.entries
                        .mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                        .toMap()
                }
                .orEmpty()

            val paymentMap = (orderSnapshot.get("payment") as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()
            paymentMap["status"] = "cancelled"

            restoredSizesByProduct.forEach { (productId, restoredSizes) ->
                if (restoredSizes.isNotEmpty()) {
                    val productRef = productRefs[productId]
                        ?: return@forEach
                    transaction.update(productRef, "sizes", restoredSizes)
                }
            }

            transaction.update(
                orderRef,
                mapOf(
                    "status" to "cancelled",
                    "payment" to paymentMap,
                    "statusHistory" to listOf(
                        mapOf(
                            "status" to "cancelled",
                            "timestamp" to Timestamp.now()
                        )
                    ) + currentHistory,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()

        "Order cancelled"
    }

    suspend fun reorderOrder(orderId: String): String = withContext(Dispatchers.IO) {
        val order = getOrderById(orderId) ?: throw IllegalStateException("Order not found.")
        if (order.items.isEmpty()) throw IllegalStateException("No items available to reorder.")

        var added = 0
        var skipped = 0

        order.items.forEach { item ->
            try {
                cartRepository.addToCart(item.productId, item.size, item.quantity)
                added++
            } catch (_: Exception) {
                skipped++
            }
        }

        if (added == 0) {
            throw IllegalStateException("Unable to add these items back to cart.")
        }

        if (skipped > 0) {
            "$added item(s) added back to cart, $skipped skipped."
        } else {
            "Items added back to cart"
        }
    }

    suspend fun getOrders(): List<OrderRecord> = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext emptyList()
        db.collection("orders")
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .map(::parseOrderSnapshot)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
    }
}
