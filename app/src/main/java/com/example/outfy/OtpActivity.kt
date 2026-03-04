package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.outfy.viewmodel.AuthViewModel

class OtpActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel
    private lateinit var verificationId: String

    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var otp5: EditText
    private lateinit var otp6: EditText
    private lateinit var verifyButton: Button
    private lateinit var otpSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        
        verificationId = intent.getStringExtra("verificationId")
            ?: throw IllegalStateException("Verification ID missing")
        
        val phone = intent.getStringExtra("phone") ?: ""

        otp1 = findViewById(R.id.otp1)
        otp2 = findViewById(R.id.otp2)
        otp3 = findViewById(R.id.otp3)
        otp4 = findViewById(R.id.otp4)
        otp5 = findViewById(R.id.otp5)
        otp6 = findViewById(R.id.otp6)
        verifyButton = findViewById(R.id.verifyButton)
        otpSubtitle = findViewById(R.id.otpSubtitle)

        otpSubtitle.text = getString(R.string.otp_subtitle, "+91 $phone")

        setupOtpAutoAdvance()

        verifyButton.setOnClickListener {
            val otp = otp1.text.toString() +
                    otp2.text.toString() +
                    otp3.text.toString() +
                    otp4.text.toString() +
                    otp5.text.toString() +
                    otp6.text.toString()

            if (otp.length == 6) {
                viewModel.verifyOtp(verificationId, otp)
            }
        }

        // 1. Observe authResult -> Check registration status
        viewModel.authResult.observe(this) { user ->
            if (user != null) {
                viewModel.checkIfUserRegistered(user)
            }
        }

        // 2. Observe isUserRegistered -> Navigate
        viewModel.isUserRegistered.observe(this) { registered ->
            if (registered) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
            finish()
        }

        viewModel.errorMessage.observe(this) {
            // Handle error
        }
    }

    private fun setupOtpAutoAdvance() {
        otp1.addTextChangedListener(GenericTextWatcher(otp1, otp2))
        otp2.addTextChangedListener(GenericTextWatcher(otp2, otp3))
        otp3.addTextChangedListener(GenericTextWatcher(otp3, otp4))
        otp4.addTextChangedListener(GenericTextWatcher(otp4, otp5))
        otp5.addTextChangedListener(GenericTextWatcher(otp5, otp6))
        otp6.addTextChangedListener(GenericTextWatcher(otp6, null))
    }

    inner class GenericTextWatcher(private val currentView: EditText, private val nextView: EditText?) : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            val text = editable.toString()
            if (text.length == 1) {
                nextView?.requestFocus()
            }
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
}