package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.outfy.viewmodel.AuthViewModel

class OtpActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel
    private lateinit var verificationId: String
    private var resendTimer: CountDownTimer? = null

    // ── FIX 1: navigation guard ───────────────────────────────────
    // Prevents multiple firings from navigating multiple times
    private var hasNavigated = false

    private lateinit var otp1: EditText
    private lateinit var otp2: EditText
    private lateinit var otp3: EditText
    private lateinit var otp4: EditText
    private lateinit var otp5: EditText
    private lateinit var otp6: EditText
    private lateinit var verifyButton: Button
    private lateinit var otpSubtitle: TextView
    private lateinit var resendText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        verificationId = intent.getStringExtra("verificationId") ?: run {
            Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val phone = intent.getStringExtra("phone") ?: ""

        otp1 = findViewById(R.id.otp1)
        otp2 = findViewById(R.id.otp2)
        otp3 = findViewById(R.id.otp3)
        otp4 = findViewById(R.id.otp4)
        otp5 = findViewById(R.id.otp5)
        otp6 = findViewById(R.id.otp6)
        verifyButton = findViewById(R.id.verifyButton)
        otpSubtitle = findViewById(R.id.otpSubtitle)
        resendText = findViewById(R.id.resendText)

        otpSubtitle.text = getString(R.string.otp_subtitle, "+91 $phone")

        setupOtpAutoAdvance()
        startResendTimer()

        verifyButton.setOnClickListener {
            val otp = otp1.text.toString() +
                    otp2.text.toString() +
                    otp3.text.toString() +
                    otp4.text.toString() +
                    otp5.text.toString() +
                    otp6.text.toString()

            if (otp.length == 6) {
                viewModel.verifyOtp(verificationId, otp)
            } else {
                Toast.makeText(this, "Enter complete OTP", Toast.LENGTH_SHORT).show()
            }
        }

        resendText.setOnClickListener {
            viewModel.sendOtp(this, "+91$phone")
            startResendTimer()
        }

        viewModel.verificationId.observe(this) { newId ->
            if (newId != null) verificationId = newId
        }

        viewModel.authResult.observe(this) { user ->
            // ── FIX 2: guard here too ─────────────────────────────
            // authResult can fire multiple times — only act once
            if (user != null && !hasNavigated) {
                viewModel.checkIfUserRegistered(user)
            }
        }

        viewModel.isUserRegistered.observe(this) { registered ->
            // ── FIX 3: hasNavigated prevents multiple launches ────
            if (registered != null && !hasNavigated) {
                hasNavigated = true
                if (registered) {
                    // ── FIX 4: clear entire back stack ───────────
                    // User can't press back to return to OTP screen
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                } else {
                    val intent = Intent(this, RegisterActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            verifyButton.isEnabled = !loading
        }
    }

    private fun startResendTimer() {
        resendTimer?.cancel()
        resendText.isEnabled = false
        resendTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                resendText.text = "Resend in ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                resendText.isEnabled = true
                resendText.text = "Resend OTP"
            }
        }.start()
    }

    // ── FIX 5: removed resetRegistrationState() from onStop ──────
    // OLD code called resetRegistrationState() in onStop()
    // This caused state to reset when user minimized app
    // Firebase callback would then re-fire and re-launch the activity
    // That's why the app was forcing itself to foreground

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }

    private fun setupOtpAutoAdvance() {
        setupView(otp1, otp2, null)
        setupView(otp2, otp3, otp1)
        setupView(otp3, otp4, otp2)
        setupView(otp4, otp5, otp3)
        setupView(otp5, otp6, otp4)
        setupView(otp6, null, otp5)
    }

    private fun setupView(currentView: EditText, nextView: EditText?, previousView: EditText?) {
        currentView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(editable: Editable?) {
                if (editable.toString().length == 1) nextView?.requestFocus()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        currentView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DEL &&
                event.action == KeyEvent.ACTION_DOWN &&
                currentView.text.isEmpty()) {
                previousView?.requestFocus()
            }
            false
        }
    }
}