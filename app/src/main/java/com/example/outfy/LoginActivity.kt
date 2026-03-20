package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.outfy.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText

class LoginActivity : AppCompatActivity() {

    private lateinit var phoneEditText: TextInputEditText
    private lateinit var getOtpButton: Button
    private lateinit var googleButton: Button
    private lateinit var viewModel: AuthViewModel
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loginContainer: FrameLayout
    private lateinit var loadingProgressBar: ProgressBar

    // ── FIX 1: navigation guard ───────────────────────────────────
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // ── FIX 2: prevent keyboard auto-open ────────────────────
        // Clear focus from phoneEditText so keyboard doesn't open on launch
        phoneEditText = findViewById(R.id.phoneEditText)
        phoneEditText.clearFocus()

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        getOtpButton = findViewById(R.id.getOtpButton)
        googleButton = findViewById(R.id.googleButton)
        loginContainer = findViewById(R.id.loginContainer)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        getOtpButton.setOnClickListener {
            val phone = phoneEditText.text.toString().trim()
            if (phone.length == 10 && phone.all { it.isDigit() }) {
                viewModel.sendOtp(this, "+91$phone")
            } else {
                phoneEditText.error = "Enter valid 10 digit number"
            }
        }

        googleButton.setOnClickListener {
            googleLauncher.launch(googleSignInClient.signInIntent)
        }

        viewModel.verificationId.observe(this) { id ->
            // ── FIX 3: guard + reset after navigating ─────────────
            // Without this, coming back to LoginActivity re-triggers
            // navigation because LiveData still holds the old value
            if (id != null && !hasNavigated) {
                hasNavigated = true
                val intent = Intent(this, OtpActivity::class.java)
                intent.putExtra("verificationId", id)
                intent.putExtra("phone", phoneEditText.text.toString().trim())
                startActivity(intent)
                viewModel.resetVerificationId()  // clear so back nav doesn't re-trigger
            }
        }

        viewModel.authResult.observe(this) { user ->
            if (user != null && !hasNavigated) {
                viewModel.checkIfUserRegistered(user)
            }
        }

        viewModel.isUserRegistered.observe(this) { registered ->
            if (registered != null && !hasNavigated) {
                hasNavigated = true
                val intent = if (registered) {
                    Intent(this, HomeActivity::class.java)
                } else {
                    Intent(this, RegisterActivity::class.java).apply {
                        putExtra("authProvider", "google") // ← add this
                    }
                }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            getOtpButton.isEnabled = !loading
            googleButton.isEnabled = !loading
            loginContainer.visibility = if (loading) View.GONE else View.VISIBLE
            loadingProgressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken == null) {
                    Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                viewModel.signInWithGoogle(idToken)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-login if already authenticated
        val user = viewModel.checkUser()
        if (user != null && !hasNavigated) {
            viewModel.checkIfUserRegistered(user)
        }
    }


}