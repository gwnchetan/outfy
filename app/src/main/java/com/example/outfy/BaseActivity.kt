package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.OptIn

open class BaseActivity : AppCompatActivity() {

    private var globalFab: FloatingActionButton? = null
    private var cartBadge: BadgeDrawable? = null
    private lateinit var cartViewModel: CartViewModel

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        setupGlobalCartFab()
    }

    @OptIn(ExperimentalBadgeUtils::class)
    private fun setupGlobalCartFab() {
        val pageName = this.javaClass.simpleName
        val allowedPages = listOf("HomeActivity", "SearchActivity")
        
        if (!allowedPages.contains(pageName)) {
            return
        }

        val root = findViewById<ViewGroup>(android.R.id.content)
        
        val fab = FloatingActionButton(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_cart)
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.black))
            compatElevation = 8f * resources.displayMetrics.density
        }
        
        fab.imageTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.white))

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            val margin = (16 * resources.displayMetrics.density).toInt()
            val bottomNavMargin = if (pageName == "HomeActivity" || pageName == "SearchActivity") {
                (100 * resources.displayMetrics.density).toInt()
            } else {
                margin
            }
            setMargins(margin, margin, margin, bottomNavMargin)
        }
        root.addView(fab, params)
        globalFab = fab

        cartViewModel = ViewModelProvider(this)[CartViewModel::class.java]
        
        cartBadge = BadgeDrawable.create(this).apply {
            backgroundColor = getColor(R.color.red)
            isVisible = false
        }

        fab.viewTreeObserver.addOnGlobalLayoutListener {
            @Suppress("UnsafeOptInUsageError")
            cartBadge?.let { badge -> BadgeUtils.attachBadgeDrawable(badge, fab) }
        }

        cartViewModel.cartCount.observe(this) { count ->
            if (count != null && count > 0) {
                cartBadge?.isVisible = true
                cartBadge?.clearNumber() 
            } else {
                cartBadge?.isVisible = false
            }
        }

        fab.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }

        cartViewModel.loadCartCount()
    }
}
