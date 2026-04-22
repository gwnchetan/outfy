package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.outfy.adapter.OrderAdapter
import com.example.outfy.databinding.ActivityOrdersBinding
import com.example.outfy.viewmodel.OrdersViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils

class OrdersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOrdersBinding
    private val viewModel: OrdersViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var cartBadge: BadgeDrawable
    private var allOrders = emptyList<com.example.outfy.model.OrderRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrdersBinding.inflate(layoutInflater)
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

        binding.btnBack.setOnClickListener { finish() }
        setupFilters()

        orderAdapter = OrderAdapter { order ->
            startActivity(
                Intent(this, TrackOrderActivity::class.java).apply {
                    putExtra(TrackOrderActivity.EXTRA_ORDER_ID, order.orderId)
                }
            )
        }
        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(this@OrdersActivity)
            adapter = orderAdapter
        }

        observeViewModel()
        viewModel.loadOrders()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadOrders()
    }

    private fun observeViewModel() {
        viewModel.orders.observe(this) { orders ->
            allOrders = orders
            applyFilter()
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
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

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { applyFilter() }
        binding.chipActive.setOnClickListener { applyFilter() }
        binding.chipDelivered.setOnClickListener { applyFilter() }
        binding.chipCancelled.setOnClickListener { applyFilter() }
    }

    private fun applyFilter() {
        val filtered = when {
            binding.chipActive.isChecked -> allOrders.filter {
                it.status in listOf("order_placed", "departure", "arrived_city", "out_for_delivery")
            }
            binding.chipDelivered.isChecked -> allOrders.filter { it.status == "delivered" }
            binding.chipCancelled.isChecked -> allOrders.filter { it.status == "cancelled" }
            else -> allOrders
        }
        orderAdapter.submitList(filtered)
        binding.emptyState.isVisible = filtered.isEmpty()
    }
}
