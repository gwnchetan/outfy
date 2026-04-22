package com.example.outfy.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot

data class ProductSize(
    val label: String,
    val stock: Int
)

data class SizeChartRow(
    val size: String,
    val firstValue: String,
    val secondValue: String
)

data class SizeChartSpec(
    val title: String,
    val firstHeader: String,
    val secondHeader: String,
    val rows: List<SizeChartRow>
)

data class Product(
    @DocumentId
    val productId: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val target: String = "",
    val price: Double = 0.0,
    val originalPrice: Double = 0.0,
    val imageURLs: List<String> = emptyList(),
    val imageUrl: String = "",
    val sizes: List<Map<String, Any?>> = emptyList(),
    val tags: List<String> = emptyList(),
    val stock: Int = 0,
    val status: String = "",
    val isActive: Boolean = true,
    val ratingCount: Int = 0,
    val ratingAverage: Double = 0.0
)

fun Product.imageGallery(): List<String> {
    val urls = imageURLs.filter { it.isNotBlank() }
    return if (urls.isNotEmpty()) urls else listOfNotNull(imageUrl.takeIf { it.isNotBlank() })
}

fun Product.hasDiscount(): Boolean = originalPrice > price

fun Product.displayOriginalPrice(): Double = if (hasDiscount()) originalPrice else price

fun Product.displaySubtitle(): String =
    listOf(category.trim(), target.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" • ")

fun Product.displayTags(): List<String> =
    if (tags.isNotEmpty()) {
        tags.filter { it.isNotBlank() }
    } else {
        listOf(category.trim(), target.trim()).filter { it.isNotEmpty() }
    }

fun Product.availableSizes(): List<ProductSize> =
    sizes.mapNotNull { size ->
        val label = ((size["label"] ?: size["size"]) as? String)?.trim().orEmpty()
        if (label.isEmpty()) {
            null
        } else {
            ProductSize(
                label = label,
                stock = (size["stock"] as? Number)?.toInt() ?: 0
            )
        }
    }

fun Product.stockForSize(label: String): Int =
    availableSizes().firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }?.stock ?: 0

fun Product.hasSizeOptions(): Boolean = availableSizes().isNotEmpty()

fun Product.isInStock(): Boolean {
    val sizeOptions = availableSizes()
    return sizeOptions.isNotEmpty() && sizeOptions.any { it.stock > 0 }
}

fun Product.isAvailable(): Boolean {
    val normalizedStatus = status.trim().lowercase()
    return if (normalizedStatus.isNotEmpty()) {
        normalizedStatus == "active"
    } else {
        isActive
    }
}

fun Product.hasRatings(): Boolean = ratingCount > 0 && ratingAverage > 0.0

fun DocumentSnapshot.toProductOrNull(): Product? {
    val payload = data ?: return null
    val status = getString("status").orEmpty()
    val rawImageUrls = (payload["imageURLs"] as? List<*>)
        ?.mapNotNull { it as? String }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    val rawSizes = (payload["sizes"] as? List<*>)
        ?.mapNotNull { size ->
            (size as? Map<*, *>)?.entries
                ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                ?.toMap()
        }
        .orEmpty()

    return Product(
        productId = getString("productId").takeUnless { it.isNullOrBlank() } ?: id,
        name = getString("name").orEmpty(),
        description = getString("description").orEmpty(),
        category = getString("category").orEmpty(),
        target = getString("target").orEmpty(),
        price = (payload["price"] as? Number)?.toDouble() ?: 0.0,
        originalPrice = ((payload["originalPrice"] as? Number)?.toDouble()
            ?: (payload["price"] as? Number)?.toDouble()
            ?: 0.0),
        imageURLs = rawImageUrls,
        imageUrl = getString("imageUrl").orEmpty(),
        sizes = rawSizes,
        tags = (payload["tags"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
        stock = (payload["stock"] as? Number)?.toInt() ?: 0,
        status = status,
        isActive = (payload["isActive"] as? Boolean) ?: status.equals("active", ignoreCase = true) || status.isBlank(),
        ratingCount = (payload["ratingCount"] as? Number)?.toInt() ?: 0,
        ratingAverage = (payload["ratingAverage"] as? Number)?.toDouble() ?: 0.0
    )
}

fun Product.isBottom(): Boolean {
    val lowerCategory = category.lowercase().trim()
    val bottomKeywords = listOf("bottom", "jeans", "pants", "pant", "cargo", "shorts", "short", "skirt", "trousers")
    return bottomKeywords.any { lowerCategory.contains(it) }
}

fun Product.isTop(): Boolean = !isBottom()

private fun Product.normalizedTarget(): String = target.trim().lowercase()

private fun Product.isWomenTarget(): Boolean = normalizedTarget() == "women"

private val menTopChartRows = listOf(
    SizeChartRow("XS", "34\"", "25\""),
    SizeChartRow("S", "36\"", "26\""),
    SizeChartRow("M", "38\"", "27\""),
    SizeChartRow("L", "40\"", "28\""),
    SizeChartRow("XL", "42\"", "29\""),
    SizeChartRow("XXL", "44\"", "30\"")
)

private val womenTopChartRows = listOf(
    SizeChartRow("XS", "32\"", "23\""),
    SizeChartRow("S", "34\"", "24\""),
    SizeChartRow("M", "36\"", "25\""),
    SizeChartRow("L", "38\"", "26\""),
    SizeChartRow("XL", "40\"", "27\""),
    SizeChartRow("XXL", "42\"", "28\"")
)

private val menBottomChartRows = listOf(
    SizeChartRow("XS", "28\"", "39\""),
    SizeChartRow("S", "30\"", "40\""),
    SizeChartRow("M", "32\"", "41\""),
    SizeChartRow("L", "34\"", "42\""),
    SizeChartRow("XL", "36\"", "43\""),
    SizeChartRow("XXL", "38\"", "44\"")
)

private val womenBottomChartRows = listOf(
    SizeChartRow("XS", "26\"", "38\""),
    SizeChartRow("S", "28\"", "39\""),
    SizeChartRow("M", "30\"", "40\""),
    SizeChartRow("L", "32\"", "41\""),
    SizeChartRow("XL", "34\"", "42\""),
    SizeChartRow("XXL", "36\"", "43\"")
)

private fun Product.filterChartRows(rows: List<SizeChartRow>): List<SizeChartRow> {
    val availableLabels = availableSizes()
        .map { it.label.trim().uppercase() }
        .toSet()

    if (availableLabels.isEmpty()) return rows

    val matchingRows = rows.filter { it.size.uppercase() in availableLabels }
    return if (matchingRows.isNotEmpty()) matchingRows else rows
}

fun Product.sizeChartSpec(): SizeChartSpec {
    val isBottomWear = isBottom()
    val isWomenWear = isWomenTarget()

    val title = when {
        isBottomWear && isWomenWear -> "Women's Bottom Size Guide"
        isBottomWear -> "Men's Bottom Size Guide"
        isWomenWear -> "Women's Top Size Guide"
        else -> "Men's Top Size Guide"
    }

    val firstHeader = when {
        isBottomWear -> "Waist"
        isWomenWear -> "Bust"
        else -> "Chest"
    }

    val rows = when {
        isBottomWear && isWomenWear -> womenBottomChartRows
        isBottomWear -> menBottomChartRows
        isWomenWear -> womenTopChartRows
        else -> menTopChartRows
    }

    return SizeChartSpec(
        title = title,
        firstHeader = firstHeader,
        secondHeader = "Length",
        rows = filterChartRows(rows)
    )
}
