package com.example.outfy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.outfy.adapter.ProductAdapter
import com.example.outfy.adapter.SimilarProductAdapter
import com.example.outfy.databinding.ActivitySearchBinding
import com.example.outfy.viewmodel.CartViewModel
import com.example.outfy.viewmodel.SearchViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils

@OptIn(ExperimentalBadgeUtils::class)
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val viewModel: SearchViewModel by viewModels()
    private lateinit var resultsAdapter: ProductAdapter
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var cartBadge: BadgeDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupResultsList()
        setupSearch()
        setupFilters()
        setupNavigation()
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

        viewModel.loadCatalog()
        cartViewModel.loadCartCount()
    }

    private fun setupResultsList() {
        resultsAdapter = ProductAdapter { product ->
            startActivity(
                Intent(this, ProductDetailActivity::class.java).apply {
                    putExtra("productId", product.productId)
                }
            )
        }

        binding.rvSearchResults.apply {
            layoutManager = GridLayoutManager(this@SearchActivity, 2)
            adapter = resultsAdapter
            isNestedScrollingEnabled = false
        }

        binding.rvBestSellers.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.isNotBlank() == true
                val start = binding.etSearch.compoundDrawables[0]
                val top = binding.etSearch.compoundDrawables[1]
                val end = if (hasText) ContextCompat.getDrawable(this@SearchActivity, android.R.drawable.ic_menu_close_clear_cancel) else null
                val bottom = binding.etSearch.compoundDrawables[3]
                binding.etSearch.setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom)
                viewModel.updateQuery(s?.toString().orEmpty())
            }
        })

        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else {
                false
            }
        }

        binding.etSearch.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = binding.etSearch.compoundDrawables[2]
                if (drawableEnd != null) {
                    val drawableStartX = binding.etSearch.right - drawableEnd.bounds.width() - binding.etSearch.paddingEnd
                    if (event.rawX >= drawableStartX) {
                        binding.etSearch.setText("")
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun setupFilters() {
        binding.chipAll.setOnClickListener { selectTargetFilter(null) }
        binding.chipMen.setOnClickListener { selectTargetFilter("men") }
        binding.chipWomen.setOnClickListener { selectTargetFilter("women") }
        binding.chipKids.setOnClickListener { selectTargetFilter("kids") }
        binding.chipSale.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSaleOnly(isChecked)
        }
    }

    private fun setupNavigation() {
        binding.btnBack.setOnClickListener { finish() }

        binding.bottomNav.selectedItemId = R.id.nav_search
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    finish()
                    true
                }
                R.id.nav_search -> true
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
    }

    private fun observeViewModel() {
        viewModel.results.observe(this) { products ->
            resultsAdapter.submitList(products)
            binding.tvEmptyResults.isVisible = products.isEmpty()
        }

        viewModel.bestSellers.observe(this) { products ->
            binding.rvBestSellers.adapter = SimilarProductAdapter(products) { product ->
                startActivity(
                    Intent(this, ProductDetailActivity::class.java).apply {
                        putExtra("productId", product.productId)
                    }
                )
            }
            binding.sectionBestSellers.isVisible = products.isNotEmpty()
        }

        viewModel.resultTitle.observe(this) { title ->
            binding.tvResultsTitle.text = title
        }

        viewModel.resultMeta.observe(this) { meta ->
            binding.tvResultsMeta.text = meta
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        cartViewModel.cartCount.observe(this) { count: Int ->
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

    private fun selectTargetFilter(target: String?) {
        viewModel.setTargetFilter(target)
        binding.chipAll.isChecked = target == null
        binding.chipMen.isChecked = target == "men"
        binding.chipWomen.isChecked = target == "women"
        binding.chipKids.isChecked = target == "kids"
    }
}
