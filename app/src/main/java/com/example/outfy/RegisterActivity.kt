package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
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
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    // ── 1. UI REFERENCES ──────────────────────────────────────────
    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var dobEditText: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var continueButton: Button

    // ── 2. VIEWMODEL ──────────────────────────────────────────────
    private val viewModel: AuthViewModel by viewModels()

    // ── 3. SETUP ──────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        bindViews()

        val authProvider = intent.getStringExtra("authProvider") ?: "phone"
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (authProvider == "google") {
            // pre-fill name and email from Google
            currentUser?.displayName?.let { nameEditText.setText(it) }
            currentUser?.email?.let {
                emailEditText.setText(it)
                emailEditText.isEnabled = false  // lock — can't change Google email
            }
            // show phone field
            phoneLayout.visibility = View.VISIBLE
        } else {
            // phone user — phone already known, hide field
            phoneLayout.visibility = View.GONE
        }

        setupGenderDropdown()
        setupDatePicker()
        observeViewModel()

        continueButton.setOnClickListener {
            handleContinue()
        }
    }

    private fun bindViews() {
        nameEditText = findViewById(R.id.nameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        phoneLayout = findViewById(R.id.phoneLayout)
        phoneEditText = findViewById(R.id.phoneEditText)
        dobEditText = findViewById(R.id.dobEditText)
        genderDropdown = findViewById(R.id.genderDropdown)
        continueButton = findViewById(R.id.continueButton)
    }

    private fun setupGenderDropdown() {
        val genders = listOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        genderDropdown.setAdapter(adapter)
    }

    private fun setupDatePicker() {
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
    private fun handleContinue() {
        val name   = nameEditText.text.toString().trim()
        val email  = emailEditText.text.toString().trim()
        val dob    = dobEditText.text.toString().trim()
        val gender = genderDropdown.text.toString().trim()
        val authProvider = intent.getStringExtra("authProvider") ?: "phone"

        // Name check
        if (name.isEmpty()) {
            nameEditText.error = "Enter full name"
            return
        }

        // Email check
        if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter a valid email"
            return
        }

        // validate phone only for Google users
        if (authProvider == "google") {
            val phone = phoneEditText.text.toString().trim()
            if (phone.length != 10 || !phone.all { it.isDigit() }) {
                phoneEditText.error = "Enter valid 10 digit number"
                return
            }
        }

        // DOB check
        if (dob.isEmpty()) {
            dobEditText.error = "Select date of birth"
            return
        }

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
            name         = name,
            email        = email.ifEmpty { null },
            dob          = dob,
            gender       = gender,
            phone        = if (authProvider == "google") "+91${phoneEditText.text.toString().trim()}" else null,
            authProvider = authProvider
        )
    }

    private fun isAgeValid(dob: String): Boolean {
        val birthDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dob) ?: return false
        val today = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        return age >= 13
    }

    // ── 5. OBSERVE RESULT ─────────────────────────────────────────
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