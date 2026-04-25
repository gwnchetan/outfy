package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.example.outfy.databinding.ActivitySecurityBinding
import com.example.outfy.model.Users
import com.example.outfy.viewmodel.SecurityViewModel
import com.google.android.material.chip.Chip
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthProvider

class SecurityActivity : BaseActivity() {
    private lateinit var binding: ActivitySecurityBinding
    private val viewModel: SecurityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSendReset.setOnClickListener { viewModel.sendResetEmail() }

        observeViewModel()
        viewModel.loadSecurityState()
    }

    private fun observeViewModel() {
        val render: () -> Unit = {
            renderSecurity(
                profile = viewModel.profile.value,
                providerIds = viewModel.providerIds.value.orEmpty()
            )
        }

        viewModel.profile.observe(this) { render() }
        viewModel.providerIds.observe(this) { render() }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading || viewModel.isSending.value == true
        }

        viewModel.isSending.observe(this) { isSending ->
            binding.progressBar.isVisible = isSending || viewModel.isLoading.value == true
            binding.btnSendReset.isEnabled = !isSending
            binding.btnSendReset.text =
                if (isSending) getString(R.string.sending_reset_link) else getString(R.string.send_reset_link)
        }

        viewModel.message.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    private fun renderSecurity(profile: Users?, providerIds: List<String>) {
        val resolvedProviderIds = resolveProviderIds(profile, providerIds)
        val canResetPassword = EmailAuthProvider.PROVIDER_ID in resolvedProviderIds

        binding.tvLinkedEmail.text = profile?.email ?: getString(R.string.not_linked)
        binding.tvLinkedPhone.text = profile?.phone ?: getString(R.string.not_linked)
        binding.providerChipGroup.removeAllViews()

        if (resolvedProviderIds.isEmpty()) {
            addProviderChip(getString(R.string.provider_unknown))
        } else {
            resolvedProviderIds.forEach { providerId ->
                addProviderChip(
                    when (providerId) {
                        GoogleAuthProvider.PROVIDER_ID -> getString(R.string.provider_google)
                        PhoneAuthProvider.PROVIDER_ID -> getString(R.string.provider_phone_otp)
                        EmailAuthProvider.PROVIDER_ID -> getString(R.string.provider_email_password)
                        else -> providerId
                    }
                )
            }
        }

        when {
            canResetPassword -> {
                binding.tvPasswordTitle.text = getString(R.string.security_password_title_email)
                binding.tvPasswordBody.text = getString(R.string.security_password_body_email)
            }
            GoogleAuthProvider.PROVIDER_ID in resolvedProviderIds -> {
                binding.tvPasswordTitle.text = getString(R.string.security_password_title_google)
                binding.tvPasswordBody.text = getString(R.string.security_password_body_google)
            }
            PhoneAuthProvider.PROVIDER_ID in resolvedProviderIds -> {
                binding.tvPasswordTitle.text = getString(R.string.security_password_title_phone)
                binding.tvPasswordBody.text = getString(R.string.security_password_body_phone)
            }
            else -> {
                binding.tvPasswordTitle.text = getString(R.string.security_password_title_unknown)
                binding.tvPasswordBody.text = getString(R.string.security_password_body_unknown)
            }
        }

        binding.btnSendReset.isVisible = canResetPassword
    }

    private fun resolveProviderIds(profile: Users?, providerIds: List<String>): List<String> {
        if (providerIds.isNotEmpty()) return providerIds
        return when (profile?.authProvider?.lowercase()) {
            "google" -> listOf(GoogleAuthProvider.PROVIDER_ID)
            "phone" -> listOf(PhoneAuthProvider.PROVIDER_ID)
            "password", "email" -> listOf(EmailAuthProvider.PROVIDER_ID)
            else -> emptyList()
        }
    }

    private fun addProviderChip(label: String) {
        val chip = Chip(this).apply {
            text = label
            isCheckable = false
            isClickable = false
            setEnsureMinTouchTargetSize(false)
        }
        binding.providerChipGroup.addView(chip)
    }
}
