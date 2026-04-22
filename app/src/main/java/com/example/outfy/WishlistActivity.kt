package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.outfy.adapter.ProductAdapter
import com.example.outfy.databinding.ActivityWishlistBinding
import com.example.outfy.viewmodel.WishlistViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils

class WishlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWishlistBinding
    private val viewModel: WishlistViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var cartBadge: BadgeDrawable
    private lateinit var productAdapter: ProductAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWishlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productAdapter = ProductAdapter { product ->
            startActivity(
                Intent(this, ProductDetailActivity::class.java).apply {
                    putExtra("productId", product.productId)
                }
            )
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.rvWishlist.apply {
            layoutManager = GridLayoutManager(this@WishlistActivity, 2)
            adapter = productAdapter
        }

        cartBadge = BadgeDrawable.create(this).apply {
            backgroundColor = getColor(R.color.red)
            badgeTextColor = getColor(R.color.white)
            maxCharacterCount = 2
        }
        @Suppress("UnsafeOptInUsageError")
        binding.fabCart.viewTreeObserver.addOnGlobalLayoutListener {
            BadgeUtils.attachBadgeDrawable(cartBadge, binding.fabCart)
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadWishlist()
        cartViewModel.loadCartCount()
    }

    private fun observeViewModel() {
        viewModel.products.observe(this) { products ->
            productAdapter.submitList(products)
            binding.tvWishlistCount.text = "${products.size} saved"
            val shouldShowEmpty = products.isEmpty() && binding.progressBar.visibility != View.VISIBLE
            binding.tvEmptyWishlist.visibility = if (shouldShowEmpty) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.tvEmptyWishlist.visibility = View.GONE
            }
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
}
