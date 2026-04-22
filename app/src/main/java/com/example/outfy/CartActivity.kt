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
import com.example.outfy.adapter.CartItemAdapter
import com.example.outfy.databinding.ActivityCartBinding
import com.example.outfy.model.hasStockIssue
import com.example.outfy.viewmodel.CartViewModel
import java.text.NumberFormat
import java.util.Locale

class CartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCartBinding
    private val viewModel: CartViewModel by viewModels()
    private lateinit var cartAdapter: CartItemAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupRecyclerView()
        setupActions()
        observeViewModel()
        viewModel.loadCart()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCart()
    }

    private fun setupRecyclerView() {
        cartAdapter = CartItemAdapter(
            onIncrease = viewModel::increaseQuantity,
            onDecrease = viewModel::decreaseQuantity,
            onRemove = viewModel::removeItem,
            onOpen = { item ->
                startActivity(
                    Intent(this, ProductDetailActivity::class.java).apply {
                        putExtra("productId", item.productId)
                    }
                )
            }
        )

        binding.rvCartItems.apply {
            layoutManager = LinearLayoutManager(this@CartActivity)
            adapter = cartAdapter
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnViewOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
        binding.btnPlaceOrder.setOnClickListener {
            startActivity(Intent(this, CheckoutActivity::class.java))
        }
        binding.btnContinueShopping.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        binding.btnViewOrdersEmpty.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.cartItems.observe(this) { items ->
            cartAdapter.submitList(items)
            binding.tvCartCount.text = resources.getQuantityString(
                R.plurals.cart_items_count,
                items.size,
                items.size
            )
            binding.emptyState.isVisible = items.isEmpty()
            binding.contentGroup.isVisible = items.isNotEmpty()
            updateCheckoutHint()
        }

        viewModel.subtotal.observe(this) { subtotal ->
            binding.tvSubtotal.text = currencyFormat.format(subtotal)
        }

        viewModel.shippingFee.observe(this) { shippingFee ->
            binding.tvShipping.text =
                if (shippingFee == 0.0) getString(R.string.free_shipping)
                else currencyFormat.format(shippingFee)
        }

        viewModel.total.observe(this) { total ->
            binding.tvTotal.text = currencyFormat.format(total)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.canPlaceOrder.observe(this) { canPlace ->
            binding.btnPlaceOrder.isEnabled = canPlace
            updateCheckoutHint()
        }

        viewModel.message.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

    }

    private fun updateCheckoutHint() {
        val hasBlockingIssue = viewModel.cartItems.value.orEmpty().any { it.hasStockIssue() }
        binding.tvCheckoutHint.isVisible = hasBlockingIssue
        binding.tvCheckoutHint.text = getString(R.string.cart_checkout_hint)
    }
}
