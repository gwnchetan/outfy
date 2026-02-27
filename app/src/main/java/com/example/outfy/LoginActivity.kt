package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var phoneEditText: TextInputEditText
    private lateinit var getOtpButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        phoneEditText = findViewById(R.id.phoneEditText)
        getOtpButton = findViewById(R.id.getOtpButton)

        getOtpButton.setOnClickListener {

            val phone = phoneEditText.text.toString().trim()

            if (phone.length == 10) {
                sendOtp("+91$phone")
            } else {
                phoneEditText.error = "Enter valid number"
            }
        }
    }

    private fun sendOtp(phoneNumber: String) {

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto verification (rare case)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            phoneEditText.error = "Verification failed: ${e.message}"
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            val intent = Intent(this@LoginActivity, OtpActivity::class.java)
            intent.putExtra("verificationId", verificationId)
            startActivity(intent)
        }
    }
}