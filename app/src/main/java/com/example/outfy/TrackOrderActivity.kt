package com.example.outfy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.outfy.adapter.OrderLineItemAdapter
import com.example.outfy.databinding.ActivityTrackOrderBinding
import com.example.outfy.databinding.ItemTimelineStepBinding
import com.example.outfy.model.ORDER_STATUS_FLOW
import com.example.outfy.model.OrderRecord
import com.example.outfy.model.canCancelOrder
import com.example.outfy.model.canReorderOrder
import com.example.outfy.model.isClosedOrder
import com.example.outfy.model.orderProgressIndex
import com.example.outfy.model.orderStatusDescription
import com.example.outfy.model.orderStatusLabel
import com.example.outfy.model.streetLine
import com.example.outfy.viewmodel.TrackOrderViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class TrackOrderActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTrackOrderBinding
    private val viewModel: TrackOrderViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var cartBadge: BadgeDrawable
    private lateinit var orderLineAdapter: OrderLineItemAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private var currentOrderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrackOrderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cartBadge = BadgeDrawable.create(this).apply {
            backgroundColor = getColor(R.color.red)
            badgeTextColor = getColor(R.color.white)
            maxCharacterCount = 2
        }
        @Suppress("UnsafeOptInUsageError")
        binding.fabCart.viewTreeObserver.addOnGlobalLayoutListener {
            BadgeUtils.attachBadgeDrawable(cartBadge, binding.fabCart)
        }
        cartViewModel.loadCartCount()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        currentOrderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        if (currentOrderId.isBlank()) {
            finish()
            return
        }

        setupRecyclerView()
        setupActions(currentOrderId)
        observeViewModel()
        viewModel.loadOrder(currentOrderId)
    }

    override fun onResume() {
        super.onResume()
        if (currentOrderId.isNotBlank()) {
            viewModel.loadOrder(currentOrderId)
        }
    }

    private fun setupRecyclerView() {
        orderLineAdapter = OrderLineItemAdapter(
            onOpenProduct = { item ->
                if (item.productId.isNotBlank()) {
                    startActivity(
                        Intent(this, ProductDetailActivity::class.java).apply {
                            putExtra("productId", item.productId)
                        }
                    )
                }
            },
            onReviewProduct = { item ->
                if (item.productId.isNotBlank()) {
                    startActivity(
                        Intent(this, ProductDetailActivity::class.java).apply {
                            putExtra("productId", item.productId)
                            putExtra(ProductDetailActivity.EXTRA_OPEN_REVIEW_COMPOSER, true)
                        }
                    )
                }
            }
        )
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(this@TrackOrderActivity)
            adapter = orderLineAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupActions(orderId: String) {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCopyId.setOnClickListener {
            copyOrderId(orderId)
        }
        binding.btnCancelOrder.setOnClickListener {
            confirmCancel(orderId)
        }
        binding.btnOrderAgain.setOnClickListener {
            viewModel.reorder(orderId)
        }
    }

    private fun observeViewModel() {
        viewModel.order.observe(this) { order ->
            binding.contentGroup.isVisible = order != null
            order?.let(::renderOrder)
        }

        viewModel.reviewedProductIds.observe(this) { reviewedProductIds ->
            val isDeliveredOrder = viewModel.order.value?.status == "delivered"
            orderLineAdapter.submitReviewState(isDeliveredOrder, reviewedProductIds)
            binding.tvDeliveredReviewHint.isVisible = isDeliveredOrder
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.isCancelling.observe(this) { isCancelling ->
            binding.btnCancelOrder.isEnabled = !isCancelling
            binding.btnCancelOrder.text =
                if (isCancelling) getString(R.string.cancelling_order)
                else getString(R.string.cancel_order)
        }

        viewModel.isReordering.observe(this) { isReordering ->
            binding.btnOrderAgain.isEnabled = !isReordering
            binding.btnOrderAgain.text =
                if (isReordering) getString(R.string.reordering_text)
                else getString(R.string.order_again)
        }

        viewModel.message.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.openCartEvent.observe(this) { shouldOpen ->
            if (shouldOpen) {
                startActivity(Intent(this, CartActivity::class.java))
                viewModel.clearOpenCartEvent()
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

    private fun renderOrder(order: OrderRecord) {
        binding.tvStatus.text = orderStatusLabel(order.status)
        binding.tvStatusDescription.text = orderStatusDescription(order.status)
        binding.tvOrderId.text = getString(R.string.track_order_id, order.orderId.takeLast(8).uppercase())
        binding.tvPlacedDate.text = order.createdAt?.toDate()?.let(dateFormat::format) ?: "Just now"
        binding.tvTotalAmount.text = currencyFormat.format(order.pricing.totalAmount)

        orderLineAdapter.submitList(order.items)
        orderLineAdapter.submitReviewState(order.status == "delivered", viewModel.reviewedProductIds.value.orEmpty())
        renderTimeline(order)

        val address = order.shippingAddress
        binding.tvAddressName.text = address?.fullName.orEmpty()
        binding.tvAddressLine.text = address?.streetLine().orEmpty()
        binding.tvAddressCity.text = listOf(
            address?.city.orEmpty(),
            address?.state.orEmpty(),
            address?.pincode.orEmpty()
        ).filter { it.isNotBlank() }.joinToString(", ")
        binding.tvAddressPhone.text = address?.phone.orEmpty()

        binding.tvPaymentLabel.text = order.payment.label
        binding.tvPaymentStatus.text = order.payment.status.replaceFirstChar { it.uppercase() }
        binding.tvOrderCount.text = resources.getQuantityString(
            R.plurals.checkout_items_count,
            order.itemCount,
            order.itemCount
        )
        binding.tvDeliveredReviewHint.isVisible = order.status == "delivered"

        binding.btnCancelOrder.isVisible = canCancelOrder(order.status)
        binding.btnOrderAgain.isVisible = canReorderOrder(order.status) || isClosedOrder(order.status)
    }

    private fun renderTimeline(order: OrderRecord) {
        binding.timelineContainer.removeAllViews()
        val progressIndex = orderProgressIndex(order.status)

        if (order.status == "cancelled") {
            binding.timelineHint.text = getString(R.string.order_cancelled_hint)
        } else {
            val currentStep = if (progressIndex >= 0) progressIndex + 1 else 1
            binding.timelineHint.text = getString(
                R.string.order_timeline_hint,
                currentStep,
                ORDER_STATUS_FLOW.size
            )
        }

        ORDER_STATUS_FLOW.forEachIndexed { index, status ->
            val itemBinding = ItemTimelineStepBinding.inflate(layoutInflater, binding.timelineContainer, false)
            itemBinding.tvStepTitle.text = orderStatusLabel(status)
            itemBinding.tvStepSubtitle.text = order.statusHistory
                .firstOrNull { it.status == status }
                ?.timestamp
                ?.toDate()
                ?.let(dateFormat::format)
                ?: orderStatusDescription(status)

            when {
                order.status == "cancelled" -> {
                    itemBinding.dotView.setBackgroundResource(R.drawable.bg_timeline_dot_idle)
                    itemBinding.connectorView.setBackgroundColor(getColor(R.color.gray_light))
                }
                index < progressIndex -> {
                    itemBinding.dotView.setBackgroundResource(R.drawable.bg_timeline_dot_completed)
                    itemBinding.connectorView.setBackgroundColor(getColor(R.color.order_status_departure))
                }
                index == progressIndex -> {
                    itemBinding.dotView.setBackgroundResource(R.drawable.bg_timeline_dot_active)
                    itemBinding.connectorView.setBackgroundColor(getColor(R.color.gray_light))
                }
                else -> {
                    itemBinding.dotView.setBackgroundResource(R.drawable.bg_timeline_dot_idle)
                    itemBinding.connectorView.setBackgroundColor(getColor(R.color.gray_light))
                }
            }

            itemBinding.connectorView.isVisible = index != ORDER_STATUS_FLOW.lastIndex
            binding.timelineContainer.addView(itemBinding.root)
        }
    }

    private fun confirmCancel(orderId: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cancel_order)
            .setMessage(R.string.cancel_order_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.cancel_order) { _, _ ->
                viewModel.cancelOrder(orderId)
            }
            .show()
    }

    private fun copyOrderId(orderId: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("order_id", orderId))
        Toast.makeText(this, R.string.order_id_copied, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "orderId"
    }
}
