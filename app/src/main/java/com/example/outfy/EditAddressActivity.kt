package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.outfy.databinding.ActivityEditAddressBinding
import com.example.outfy.model.UserAddress
import com.example.outfy.viewmodel.EditAddressViewModel
import com.example.outfy.viewmodel.CartViewModel
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils

class EditAddressActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditAddressBinding
    private val viewModel: EditAddressViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()
    private lateinit var cartBadge: BadgeDrawable
    private var hasBoundInitialAddress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditAddressBinding.inflate(layoutInflater)
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

        setupActions()
        observeViewModel()
        viewModel.loadAddress()
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSaveAddress.setOnClickListener {
            viewModel.saveAddress(readAddressFromForm())
        }
    }

    private fun observeViewModel() {
        viewModel.address.observe(this) { address ->
            if (!hasBoundInitialAddress) {
                bindAddress(address)
                hasBoundInitialAddress = true
            }
        }

        viewModel.profile.observe(this) { profile ->
            binding.tvAddressHelper.text = if (profile?.defaultAddress != null) {
                getString(R.string.saved_address_meta_existing)
            } else {
                getString(R.string.saved_address_meta_new)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.isSaving.observe(this) { isSaving ->
            binding.btnSaveAddress.isEnabled = !isSaving
            binding.btnSaveAddress.text =
                if (isSaving) getString(R.string.saving_address) else getString(R.string.save_address)
        }

        viewModel.message.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.saveCompleted.observe(this) { completed ->
            if (completed) {
                viewModel.clearSaveCompleted()
                finish()
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

    private fun bindAddress(address: UserAddress) {
        binding.etFullName.setText(address.fullName)
        binding.etPhone.setText(address.phone)
        binding.etLine1.setText(address.line1)
        binding.etLine2.setText(address.line2)
        binding.etCity.setText(address.city)
        binding.etState.setText(address.state)
        binding.etPincode.setText(address.pincode)
        if (address.label.equals("WORK", ignoreCase = true)) {
            binding.chipWork.isChecked = true
        } else {
            binding.chipHome.isChecked = true
        }
    }

    private fun readAddressFromForm(): UserAddress {
        return UserAddress(
            fullName = binding.etFullName.text?.toString().orEmpty().trim(),
            phone = binding.etPhone.text?.toString().orEmpty().trim(),
            line1 = binding.etLine1.text?.toString().orEmpty().trim(),
            line2 = binding.etLine2.text?.toString().orEmpty().trim(),
            city = binding.etCity.text?.toString().orEmpty().trim(),
            state = binding.etState.text?.toString().orEmpty().trim(),
            pincode = binding.etPincode.text?.toString().orEmpty().trim(),
            label = if (binding.chipWork.isChecked) "WORK" else "HOME"
        )
    }
}
