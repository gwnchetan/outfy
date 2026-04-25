package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.outfy.databinding.ActivityEditProfileBinding
import com.example.outfy.model.Users
import com.example.outfy.viewmodel.EditProfileViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private val viewModel: EditProfileViewModel by viewModels()
    private var hasBoundInitialProfile = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupGenderDropdown()
        setupDatePicker()
        setupActions()
        observeViewModel()
        viewModel.loadProfile()
    }

    private fun setupGenderDropdown() {
        val genders = listOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        binding.genderDropdown.setAdapter(adapter)
    }

    private fun setupDatePicker() {
        binding.etDob.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.edit_profile_dob_title)
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                        .build()
                )
                .build()

            picker.show(supportFragmentManager, "EDIT_PROFILE_DOB")
            picker.addOnPositiveButtonClickListener { selectedMs ->
                val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(Date(selectedMs))
                binding.etDob.setText(formatted)
            }
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            viewModel.saveProfile(
                name = binding.etFullName.text?.toString().orEmpty(),
                gender = binding.genderDropdown.text?.toString().orEmpty(),
                dob = binding.etDob.text?.toString().orEmpty()
            )
        }
    }

    private fun observeViewModel() {
        viewModel.profile.observe(this) { profile ->
            if (profile != null && !hasBoundInitialProfile) {
                bindProfile(profile)
                hasBoundInitialProfile = true
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        }

        viewModel.isSaving.observe(this) { isSaving ->
            binding.btnSave.isEnabled = !isSaving
            binding.btnSave.text =
                if (isSaving) getString(R.string.saving_changes) else getString(R.string.save_changes)
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
    }

    private fun bindProfile(profile: Users) {
        binding.etFullName.setText(profile.name)
        binding.etEmail.text = profile.email.orEmpty()
        binding.etPhone.text = profile.phone.orEmpty()
        binding.etDob.setText(profile.dob.orEmpty())
        binding.genderDropdown.setText(profile.gender, false)
    }
}
