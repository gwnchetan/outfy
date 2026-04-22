package com.example.outfy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.outfy.adapter.ProductAdapter
import com.example.outfy.databinding.ActivityHomeBinding
import com.example.outfy.viewmodel.HomeViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalBadgeUtils::class)
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel
    private lateinit var cartViewModel: CartViewModel
    private lateinit var productAdapter: ProductAdapter
    private lateinit var cartBadge: BadgeDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar overlap fix
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupViewModel()
        setupRecyclerView()
        setupCategoryFilter()
        setupDiscoveryActions()
        setupNavigation()
        observeData()

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            viewModel.fetchProductsForUser(userId)
        } else {
            viewModel.refreshProducts()
        }

        cartViewModel.loadCartCount()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        cartViewModel = ViewModelProvider(this)[CartViewModel::class.java]
        cartBadge = BadgeDrawable.create(this).apply {
            backgroundColor = getColor(R.color.red)
            badgeTextColor = getColor(R.color.white)
            maxCharacterCount = 2
        }
        @Suppress("UnsafeOptInUsageError")
        binding.fabCart.viewTreeObserver.addOnGlobalLayoutListener {
            BadgeUtils.attachBadgeDrawable(cartBadge, binding.fabCart)
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter { product ->
            // Navigate to product detail
            val intent = Intent(this, ProductDetailActivity::class.java)
            intent.putExtra("productId", product.productId)
            startActivity(intent)
        }
        
        binding.rvProducts.apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 2)
            adapter = productAdapter
            isNestedScrollingEnabled = false 
            itemAnimator = null
        }
    }

    private fun setupCategoryFilter() {
        binding.btnMen.setOnClickListener {
            viewModel.filterByTarget("men")
            scrollToSectionHeader()
        }

        binding.btnWomen.setOnClickListener {
            viewModel.filterByTarget("women")
            scrollToSectionHeader()
        }

        binding.btnKids.setOnClickListener {
            viewModel.filterByTarget("kids")
            scrollToSectionHeader()
        }
    }

    private fun setupDiscoveryActions() {
        binding.searchEntry.setOnClickListener {
            openSearch()
        }

        binding.btnShopBanner.setOnClickListener {
            openSearch()
        }

        binding.btnResetSection.setOnClickListener {
            viewModel.resetToDefaultFeed()
            scrollToSectionHeader()
        }

        binding.fabCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun observeData() {
        viewModel.products.observe(this) { products ->
            productAdapter.submitList(products)
        }

        viewModel.sectionTitle.observe(this) { title ->
            binding.tvSectionTitle.text = title
        }

        viewModel.sectionMeta.observe(this) { meta ->
            binding.tvSectionMeta.text = meta
        }

        cartViewModel.cartCount.observe(this) { count ->
            if (count > 0) {
                cartBadge.number = count
                cartBadge.isVisible = true
            } else {
                cartBadge.isVisible = false
            }
        }
    }

    private fun setupNavigation() {
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_search -> {
                    openSearch()
                    true
                }
                R.id.nav_favorites -> {
                    startActivity(Intent(this, WishlistActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        binding.ivCart.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    private fun scrollToSectionHeader() {
        binding.homeNestedScroll.post {
            binding.homeNestedScroll.smoothScrollTo(0, binding.sectionHeaderContainer.top)
        }
    }
}
