package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.outfy.adapter.ProductImageAdapter
import com.example.outfy.adapter.SimilarProductAdapter
import com.example.outfy.databinding.ActivityProductDetailBinding
import com.example.outfy.databinding.BottomSheetReviewFormBinding
import com.example.outfy.databinding.ItemReviewBinding
import com.example.outfy.model.Product
import com.example.outfy.model.ProductSize
import com.example.outfy.model.ReviewRecord
import com.example.outfy.model.availableSizes
import com.example.outfy.model.displayOriginalPrice
import com.example.outfy.model.displaySubtitle
import com.example.outfy.model.displayTags
import com.example.outfy.model.hasDiscount
import com.example.outfy.model.hasRatings
import com.example.outfy.model.isInStock
import com.example.outfy.model.imageGallery
import com.example.outfy.model.sizeChartSpec
import com.example.outfy.model.displayTimestamp
import com.example.outfy.model.toInitials
import com.example.outfy.viewmodel.ProductDetailViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.chip.Chip
import com.example.outfy.viewmodel.CartViewModel
import java.text.NumberFormat
import android.text.format.DateUtils
import java.util.Locale
import com.google.android.material.bottomsheet.BottomSheetDialog

class ProductDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailBinding
    private val viewModel: ProductDetailViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var cartBadge: BadgeDrawable
    private var selectedSize: String? = null
    private var currentProductId: String? = null
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    private var shouldAutoOpenReviewComposer = false
    private var hasAutoOpenedReviewComposer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productId = intent.getStringExtra("productId")
        shouldAutoOpenReviewComposer = intent.getBooleanExtra(EXTRA_OPEN_REVIEW_COMPOSER, false)
        
        // Debug check: If ID is empty, we can't fetch anything
        if (productId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Product ID is missing", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupButtons()
        observeViewModel()
        
        cartBadge = BadgeDrawable.create(this).apply {
            backgroundColor = getColor(R.color.red)
            badgeTextColor = getColor(R.color.white)
            maxCharacterCount = 2
        }
        @Suppress("UnsafeOptInUsageError")
        binding.fabCart.viewTreeObserver.addOnGlobalLayoutListener {
            BadgeUtils.attachBadgeDrawable(cartBadge, binding.fabCart)
        }
        
        // Show loading spinner
        binding.progressBar.visibility = View.VISIBLE
        currentProductId = productId
        viewModel.loadWishlistState(productId)
        viewModel.loadReviewState(productId)
        viewModel.fetchProductDetails(productId)
        cartViewModel.loadCartCount()
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnAddToCart.setOnClickListener {
            if (!binding.btnAddToCart.isEnabled) {
                Toast.makeText(this, "This product is currently out of stock", Toast.LENGTH_SHORT).show()
            } else if (binding.chipGroupSize.isVisible && selectedSize == null) {
                Toast.makeText(this, "Please select a size", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.addToCart(selectedSize)
            }
        }

        binding.btnWishlist.setOnClickListener {
            currentProductId?.let(viewModel::toggleWishlist)
        }

        binding.btnCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        binding.btnReviewAction.setOnClickListener {
            showReviewComposer(viewModel.userReview.value)
        }
    }

    private fun observeViewModel() {
        viewModel.product.observe(this) { product ->
            binding.progressBar.visibility = View.GONE
            if (product != null) {
                binding.mainContent.visibility = View.VISIBLE
                displayProductDetails(product)
                tryAutoOpenReviewComposer()
            } else {
                Toast.makeText(this, "Product details not found", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.similarProducts.observe(this) { products ->
            setupSimilarProducts(products)
        }

        viewModel.isWishlisted.observe(this) { isWishlisted ->
            binding.btnWishlist.text =
                if (isWishlisted) getString(R.string.remove_from_wishlist) else getString(R.string.wishlist)
        }

        viewModel.isWishlistUpdating.observe(this) { isUpdating ->
            binding.btnWishlist.isEnabled = !isUpdating
        }

        viewModel.wishlistMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearWishlistMessage()
            }
        }

        viewModel.cartMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearCartMessage()
            }
        }

        viewModel.isAddingToCart.observe(this) { isAdding ->
            binding.btnAddToCart.isEnabled = !isAdding && (viewModel.product.value?.isInStock() == true)
            if (isAdding) {
                binding.btnAddToCart.text = getString(R.string.adding_to_cart)
            } else {
                val product = viewModel.product.value
                binding.btnAddToCart.text =
                    if (product?.isInStock() == true) getString(R.string.add_to_cart)
                    else getString(R.string.out_of_stock)
            }
        }

        viewModel.reviews.observe(this) { reviews ->
            renderReviews(reviews)
        }

        viewModel.userReview.observe(this) {
            updateReviewActionState()
            tryAutoOpenReviewComposer()
        }

        viewModel.canReview.observe(this) {
            updateReviewActionState()
            tryAutoOpenReviewComposer()
        }

        viewModel.reviewHintMessage.observe(this) { message ->
            binding.tvReviewHint.text = message
            binding.tvReviewHint.isVisible = !message.isNullOrBlank()
            tryAutoOpenReviewComposer()
        }

        viewModel.isReviewsLoading.observe(this) { isLoading ->
            binding.progressReviews.isVisible = isLoading
            if (isLoading) {
                binding.tvReviewsEmpty.isVisible = false
            }
            tryAutoOpenReviewComposer()
        }

        viewModel.reviewMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearReviewMessage()
            }
        }

        cartViewModel.cartCount.observe(this) { count ->
            if (count > 0) {
                cartBadge.number = count
                cartBadge.isVisible = true
            } else {
                cartBadge.isVisible = false
            }
        }

        binding.fabCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun displayProductDetails(product: Product) {
        val imageGallery = product.imageGallery()
        if (imageGallery.isNotEmpty()) {
            binding.viewPagerImages.adapter = ProductImageAdapter(imageGallery)
        }

        binding.tvProductName.text = product.name
        binding.tvProductPrice.text = currencyFormat.format(product.price)
        if (product.hasDiscount()) {
            val originalPrice = product.displayOriginalPrice()
            binding.tvProductPriceOld.isVisible = true
            binding.tvProductPriceOld.text = currencyFormat.format(originalPrice)
            binding.tvProductPriceOld.paintFlags =
                binding.tvProductPriceOld.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            val discount = ((originalPrice - product.price) / originalPrice * 100).toInt()
            binding.tvDiscount.text = "$discount% OFF"
            binding.tvDiscount.isVisible = true
        } else {
            binding.tvProductPriceOld.isVisible = false
            binding.tvDiscount.isVisible = false
        }

        binding.tvCategoryTarget.text = product.displaySubtitle()
        binding.tvCategoryTarget.isVisible = binding.tvCategoryTarget.text.isNotBlank()
        binding.tvDescription.text = product.description.ifBlank { "Product details will be updated soon." }

        val sizes = product.availableSizes()
        val inStock = product.isInStock()
        binding.tvStockStatus.isVisible = !inStock
        binding.tvStockStatus.text = getString(R.string.out_of_stock)
        binding.tvSizeTitle.isVisible = sizes.isNotEmpty()
        binding.chipGroupSize.isVisible = sizes.isNotEmpty()
        setupSizeChips(sizes)

        binding.btnAddToCart.isEnabled = inStock
        binding.btnAddToCart.text =
            if (inStock) getString(R.string.add_to_cart) else getString(R.string.out_of_stock)
        binding.btnAddToCart.alpha = if (inStock) 1f else 0.7f

        val tags = product.displayTags()
        binding.chipGroupTags.isVisible = tags.isNotEmpty()
        setupTagChips(tags)

        // Update size chart based on product type (TOP or BOTTOM)
        updateSizeChart(product)
        updateReviewSummary(product)
        if (viewModel.reviews.value.orEmpty().isNotEmpty()) {
            renderReviews(viewModel.reviews.value.orEmpty())
        }
    }

    private fun setupSizeChips(sizes: List<ProductSize>) {
        binding.chipGroupSize.removeAllViews()
        selectedSize = null

        for (size in sizes) {
            val chip = Chip(this).apply {
                text = size.label
                isCheckable = size.stock > 0
                isEnabled = size.stock > 0

                setChipBackgroundColorResource(if (size.stock > 0) R.color.white else R.color.gray_light)
                setTextColor(ContextCompat.getColor(context, if (size.stock > 0) R.color.black else R.color.gray_text_light))
                setChipStrokeColorResource(R.color.gray_light)
                chipStrokeWidth = 2f

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSize = size.label
                        setChipBackgroundColorResource(R.color.black)
                        setTextColor(ContextCompat.getColor(context, R.color.white))
                    } else {
                        setChipBackgroundColorResource(R.color.white)
                        setTextColor(ContextCompat.getColor(context, R.color.black))
                    }
                }
            }
            binding.chipGroupSize.addView(chip)
        }
    }

    private fun setupTagChips(tags: List<String>) {
        binding.chipGroupTags.removeAllViews()
        for (tag in tags) {
            val chip = Chip(this).apply {
                text = tag
                isClickable = false
                setChipBackgroundColorResource(R.color.gray_light)
                setTextColor(ContextCompat.getColor(context, R.color.gray_text))
            }
            binding.chipGroupTags.addView(chip)
        }
    }

    private fun setupSimilarProducts(products: List<Product>) {
        binding.rvSimilarProducts.isVisible = products.isNotEmpty()
        if (products.isEmpty()) {
            return
        }

        binding.rvSimilarProducts.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSimilarProducts.adapter = SimilarProductAdapter(products) { similarProduct ->
            val intent = Intent(this, ProductDetailActivity::class.java)
            intent.putExtra("productId", similarProduct.productId)
            startActivity(intent)
        }
    }

    private fun updateReviewSummary(product: Product) {
        if (product.hasRatings()) {
            binding.tvRatingBig.text = String.format(Locale.US, "%.1f", product.ratingAverage)
            binding.ratingBarSmall.rating = product.ratingAverage.toFloat().coerceIn(0f, 5f)
            binding.tvReviewCount.text = resources.getQuantityString(
                R.plurals.reviews_count,
                product.ratingCount,
                product.ratingCount
            )
        } else {
            binding.tvRatingBig.text = getString(R.string.no_rating_yet)
            binding.ratingBarSmall.rating = 0f
            binding.tvReviewCount.text = getString(R.string.no_reviews_yet)
        }
    }

    private fun updateReviewActionState() {
        val existingReview = viewModel.userReview.value
        val canReview = viewModel.canReview.value == true || existingReview != null
        binding.btnReviewAction.isVisible = canReview
        binding.btnReviewAction.text =
            if (existingReview != null) getString(R.string.edit_review)
            else getString(R.string.write_review)
    }

    private fun renderReviews(reviews: List<ReviewRecord>) {
        binding.llReviews.removeAllViews()
        val showEmpty = reviews.isEmpty() && viewModel.isReviewsLoading.value != true
        binding.tvReviewsEmpty.isVisible = showEmpty

        if (reviews.isNotEmpty()) {
            val average = reviews.map { it.rating }.average()
            binding.tvRatingBig.text = String.format(Locale.US, "%.1f", average)
            binding.ratingBarSmall.rating = average.toFloat().coerceIn(0f, 5f)
            binding.tvReviewCount.text = resources.getQuantityString(
                R.plurals.reviews_count,
                reviews.size,
                reviews.size
            )
        }

        reviews.forEach { review ->
            val reviewBinding = ItemReviewBinding.inflate(LayoutInflater.from(this), binding.llReviews, false)
            reviewBinding.tvReviewerName.text = review.userName
            reviewBinding.ratingBar.rating = review.rating.toFloat()
            reviewBinding.tvReviewText.text = review.reviewText
            reviewBinding.tvReviewDate.text = review.displayTimestamp()
                ?.toDate()
                ?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }
                ?: ""
            reviewBinding.tvAvatar.text = review.toInitials()
            binding.llReviews.addView(reviewBinding.root)
        }
    }

    private fun showReviewComposer(existingReview: ReviewRecord?) {
        if (viewModel.canReview.value != true && existingReview == null) {
            val message = viewModel.reviewHintMessage.value
                ?: getString(R.string.reviews_empty)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.clearReviewMessage()
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetReviewFormBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val isEditing = existingReview != null
        sheetBinding.tvReviewSheetTitle.text =
            if (isEditing) getString(R.string.edit_review) else getString(R.string.write_review)
        sheetBinding.btnSubmitReview.text =
            if (isEditing) getString(R.string.update_review) else getString(R.string.submit_review)

        if (existingReview != null) {
            sheetBinding.ratingBarReviewInput.rating = existingReview.rating.toFloat()
            sheetBinding.etReviewText.setText(existingReview.reviewText)
        }

        val submitLabel = sheetBinding.btnSubmitReview.text
        val reviewSubmittingObserver = androidx.lifecycle.Observer<Boolean> { isSubmitting ->
            sheetBinding.btnSubmitReview.isEnabled = !isSubmitting
            sheetBinding.btnSubmitReview.text =
                if (isSubmitting) getString(R.string.submitting_review) else submitLabel
        }
        val reviewMessageObserver = androidx.lifecycle.Observer<String?> { message ->
            if (message == "Review submitted" || message == "Review updated") {
                dialog.dismiss()
            }
        }
        viewModel.isSubmittingReview.observe(this, reviewSubmittingObserver)
        viewModel.reviewMessage.observe(this, reviewMessageObserver)

        dialog.setOnDismissListener {
            viewModel.isSubmittingReview.removeObserver(reviewSubmittingObserver)
            viewModel.reviewMessage.removeObserver(reviewMessageObserver)
        }

        sheetBinding.btnSubmitReview.setOnClickListener {
            val rating = sheetBinding.ratingBarReviewInput.rating.toInt()
            val reviewText = sheetBinding.etReviewText.text?.toString().orEmpty().trim()

            sheetBinding.reviewTextLayout.error = null
            when {
                rating !in 1..5 -> Toast.makeText(this, getString(R.string.review_rating_required), Toast.LENGTH_SHORT).show()
                reviewText.isBlank() -> sheetBinding.reviewTextLayout.error = getString(R.string.review_text_required)
                else -> {
                    viewModel.submitReview(rating, reviewText)
                }
            }
        }

        dialog.show()
    }

    private fun tryAutoOpenReviewComposer() {
        if (!shouldAutoOpenReviewComposer || hasAutoOpenedReviewComposer) return
        if (viewModel.product.value == null) return
        if (viewModel.isReviewsLoading.value == true) return

        val existingReview = viewModel.userReview.value
        val canReview = viewModel.canReview.value == true || existingReview != null

        if (canReview) {
            hasAutoOpenedReviewComposer = true
            showReviewComposer(existingReview)
        } else {
            val hint = viewModel.reviewHintMessage.value
            if (!hint.isNullOrBlank()) {
                hasAutoOpenedReviewComposer = true
                Toast.makeText(this, hint, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSizeChart(product: Product) {
        val chartSpec = product.sizeChartSpec()
        val sizeChartContainer = binding.root.findViewById<View>(R.id.sizeChartContainer)
        val chartTitle = binding.root.findViewById<TextView>(R.id.sizeChartTitle)
        val col1Header = binding.root.findViewById<TextView>(R.id.sizeChartCol1Header)
        val col2Header = binding.root.findViewById<TextView>(R.id.sizeChartCol2Header)

        sizeChartContainer?.isVisible = chartSpec.rows.isNotEmpty()
        chartTitle?.text = chartSpec.title
        col1Header?.text = chartSpec.firstHeader
        col2Header?.text = chartSpec.secondHeader

        updateSizeRows(chartSpec.rows)
    }

    private fun updateSizeRows(rows: List<com.example.outfy.model.SizeChartRow>) {
        val rowIds = listOf(
            R.id.sizeChartRow1, R.id.sizeChartRow2, R.id.sizeChartRow3,
            R.id.sizeChartRow4, R.id.sizeChartRow5, R.id.sizeChartRow6
        )
        val labelIds = listOf(
            R.id.sizeRow1Label, R.id.sizeRow2Label, R.id.sizeRow3Label,
            R.id.sizeRow4Label, R.id.sizeRow5Label, R.id.sizeRow6Label
        )
        val col1Ids = listOf(
            R.id.sizeRow1Col1, R.id.sizeRow2Col1, R.id.sizeRow3Col1,
            R.id.sizeRow4Col1, R.id.sizeRow5Col1, R.id.sizeRow6Col1
        )
        val col2Ids = listOf(
            R.id.sizeRow1Col2, R.id.sizeRow2Col2, R.id.sizeRow3Col2,
            R.id.sizeRow4Col2, R.id.sizeRow5Col2, R.id.sizeRow6Col2
        )

        for (index in 0 until 6) {
            val rowData = rows.getOrNull(index)
            val rowView = binding.root.findViewById<TableRow>(rowIds[index])
            val sizeView = binding.root.findViewById<TextView>(labelIds[index])
            val col1View = binding.root.findViewById<TextView>(col1Ids[index])
            val col2View = binding.root.findViewById<TextView>(col2Ids[index])

            rowView?.isVisible = rowData != null
            if (rowData != null) {
                sizeView?.text = rowData.size
                col1View?.text = rowData.firstValue
                col2View?.text = rowData.secondValue
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_REVIEW_COMPOSER = "openReviewComposer"
    }
}
