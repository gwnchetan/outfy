package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.outfy.model.Users
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var dobEditText: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        nameEditText = findViewById(R.id.nameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        dobEditText = findViewById(R.id.dobEditText)
        genderDropdown = findViewById(R.id.genderDropdown)
        continueButton = findViewById(R.id.continueButton)

        setupGenderDropdown()
        setupDatePicker()

        continueButton.setOnClickListener {
            saveUserData()
        }
    }

    // ----------------------------
    // Gender Dropdown Setup
    // ----------------------------
    private fun setupGenderDropdown() {
        val genders = arrayOf("Male", "Female", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        genderDropdown.setAdapter(adapter)
    }

    // ----------------------------
    // Date Picker Setup
    // ----------------------------
    private fun setupDatePicker() {
        dobEditText.setOnClickListener {
            val constraints = CalendarConstraints.Builder()
                .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date of Birth")
                .setCalendarConstraints(constraints)
                .build()

            picker.show(supportFragmentManager, "DOB_PICKER")

            picker.addOnPositiveButtonClickListener { selection ->
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dobEditText.setText(sdf.format(Date(selection)))
            }
        }
    }

    // ----------------------------
    // Save User To Firestore
    // ----------------------------
    private fun saveUserData() {

        val name = nameEditText.text.toString().trim()
        val email = emailEditText.text.toString().trim()
        val dob = dobEditText.text.toString().trim()
        val gender = genderDropdown.text.toString().trim()

        // Validation
        if (name.isEmpty()) {
            nameEditText.error = "Enter full name"
            return
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.error = "Enter valid email"
            return
        }

        if (dob.isEmpty()) {
            dobEditText.error = "Select date of birth"
            return
        }

        if (gender.isEmpty()) {
            genderDropdown.error = "Select gender"
            return
        }

        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid

        if (uid == null) {
            return
        }

        val user = Users(
            name = name,
            email = email,
            dob = dob,
            gender = gender,
            phone = auth.currentUser?.phoneNumber
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(user)
            .addOnSuccessListener {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }
    }
}