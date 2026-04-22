package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.outfy.databinding.ActivityProfileBinding
import com.example.outfy.model.Users
import com.example.outfy.model.cityLine
import com.example.outfy.model.isComplete
import com.example.outfy.model.streetLine
import com.example.outfy.viewmodel.ProfileViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var cartBadge: BadgeDrawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
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

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.cardOrders.setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
        binding.cardWishlist.setOnClickListener {
            startActivity(Intent(this, WishlistActivity::class.java))
        }
        binding.rowEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        binding.rowSavedAddress.setOnClickListener {
            startActivity(Intent(this, EditAddressActivity::class.java))
        }
        binding.rowSecurity.setOnClickListener {
            startActivity(Intent(this, SecurityActivity::class.java))
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadProfile()
    }

    private fun observeViewModel() {
        viewModel.profile.observe(this) { profile ->
            renderProfile(profile)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
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

    private fun renderProfile(profile: Users?) {
        binding.tvName.text = profile?.name?.ifBlank { "Outfy Customer" } ?: "Outfy Customer"
        binding.tvHeroSubtitle.text = buildIdentitySummary(profile)
        binding.tvProviderBadge.text = providerLabel(profile)
        binding.tvEmail.text = profile?.email ?: getString(R.string.not_linked)
        binding.tvPhone.text = profile?.phone ?: getString(R.string.not_linked)
        binding.tvGender.text = profile?.gender?.ifBlank { getString(R.string.not_set) } ?: getString(R.string.not_set)
        binding.tvProvider.text = providerLabel(profile)
        binding.tvSavedAddressMeta.text = buildAddressSummary(profile)
        binding.tvSecurityMeta.text = securitySummary(profile)
    }

    private fun providerLabel(profile: Users?): String {
        return when (profile?.authProvider?.lowercase()) {
            "google" -> getString(R.string.provider_google)
            "phone" -> getString(R.string.provider_phone_otp)
            "password", "email" -> getString(R.string.provider_email_password)
            else -> getString(R.string.provider_unknown)
        }
    }

    private fun buildIdentitySummary(profile: Users?): String {
        val parts = listOfNotNull(
            profile?.email?.takeIf { it.isNotBlank() },
            profile?.phone?.takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) {
            parts.joinToString(" | ")
        } else {
            getString(R.string.profile_identity_fallback)
        }
    }

    private fun buildAddressSummary(profile: Users?): String {
        val address = profile?.defaultAddress
        if (address == null || !address.isComplete()) {
            return getString(R.string.profile_saved_address_empty)
        }

        val addressLines = listOf(address.streetLine(), address.cityLine())
            .filter { it.isNotBlank() }

        return if (addressLines.isNotEmpty()) {
            addressLines.joinToString(", ")
        } else {
            getString(R.string.profile_saved_address_ready)
        }
    }

    private fun securitySummary(profile: Users?): String {
        return when (profile?.authProvider?.lowercase()) {
            "google" -> getString(R.string.profile_security_google)
            "phone" -> getString(R.string.profile_security_phone)
            "password", "email" -> getString(R.string.profile_security_email)
            else -> getString(R.string.profile_security_generic)
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ ->
                binding.btnLogout.isEnabled = false
                viewModel.logout(googleSignInClient) {
                    startActivity(
                        Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
            }
            .show()
    }
}
