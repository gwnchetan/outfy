package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.outfy.viewmodel.AuthViewModel
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    // ── 1. UI REFERENCES ──────────────────────────────────────────
    // These connect to your XML views
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var dobEditText: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var continueButton: Button

    // ── 2. VIEWMODEL ──────────────────────────────────────────────
    // Activity talks to ViewModel, NOT Firebase directly
    private val viewModel: AuthViewModel by viewModels()

    // ── 3. SETUP ──────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        bindViews()
        setupGenderDropdown()
        setupDatePicker()
        observeViewModel()

        continueButton.setOnClickListener {
            handleContinue()
        }
    }

    private fun bindViews() {
        nameEditText     = findViewById(R.id.nameEditText)
        emailEditText    = findViewById(R.id.emailEditText)
        dobEditText      = findViewById(R.id.dobEditText)
        genderDropdown   = findViewById(R.id.genderDropdown)
        continueButton   = findViewById(R.id.continueButton)
    }

    private fun setupGenderDropdown() {
        val genders = listOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        genderDropdown.setAdapter(adapter)
    }

    private fun setupDatePicker() {
        // Opens a calendar when user taps DOB field
        dobEditText.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setCalendarConstraints(
                    CalendarConstraints.Builder()
                        .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                        .build()
                )
                .build()

            picker.show(supportFragmentManager, "DOB_PICKER")

            picker.addOnPositiveButtonClickListener { selectedMs ->
                val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(Date(selectedMs))
                dobEditText.setText(formatted)
            }
        }
    }

    // ── 4. VALIDATION ─────────────────────────────────────────────
    // Check all fields BEFORE touching Firebase
    private fun handleContinue() {
        val name   = nameEditText.text.toString().trim()
        val email  = emailEditText.text.toString().trim()
        val dob    = dobEditText.text.toString().trim()
        val gender = genderDropdown.text.toString().trim()

        // Name check
        if (name.isEmpty()) {
            nameEditText.error = "Enter full name"
            return
        }

        // Email is optional — only validate format if user typed something
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email"
            return
        }

        // DOB check
        if (dob.isEmpty()) {
            dobEditText.error = "Select date of birth"
            return
        }

        // Age must be 13+
        if (!isAgeValid(dob)) {
            dobEditText.error = "Must be at least 13 years old"
            return
        }

        // Gender check
        if (gender.isEmpty()) {
            genderDropdown.error = "Select gender"
            return
        }

        // All good — send to ViewModel
        continueButton.isEnabled = false
        viewModel.registerUser(
            name   = name,
            email  = email.ifEmpty { null },  // null if user left it blank
            dob    = dob,
            gender = gender
        )
    }

    // Age validation helper
    private fun isAgeValid(dob: String): Boolean {
        val birthDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dob) ?: return false
        val today = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        return age >= 13
    }

    // ── 5. OBSERVE RESULT ─────────────────────────────────────────
    // ViewModel tells us success or failure — we just react
    private fun observeViewModel() {
        viewModel.registerResult.observe(this) { result ->
            when {
                result.isSuccess -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                result.isFailure -> {
                    Toast.makeText(this, result.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                    continueButton.isEnabled = true
                }
            }
        }
    }
}