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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        viewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        phoneEditText = findViewById(R.id.phoneEditText)
        getOtpButton = findViewById(R.id.getOtpButton)
        googleButton = findViewById(R.id.googleButton)
        loginContainer = findViewById(R.id.loginContainer)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)

        // Configure Google Sign In
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
            val signInIntent = googleSignInClient.signInIntent
            googleLauncher.launch(signInIntent)
        }

        viewModel.verificationId.observe(this) { id ->
            if (id != null) {
                val intent = Intent(this, OtpActivity::class.java)
                intent.putExtra("verificationId", id)
                intent.putExtra("phone", phoneEditText.text.toString().trim())
                startActivity(intent)
            }
        }

        viewModel.authResult.observe(this) { user ->
            if (user != null) {
                viewModel.checkIfUserRegistered(user)
            }
        }

        viewModel.isUserRegistered.observe(this) { registered ->
            if (registered != null) {
                if (registered == true) {
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    startActivity(Intent(this, RegisterActivity::class.java))
                }
                finish()
            }
        }

        viewModel.errorMessage.observe(this) {
            if (it != null) {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            getOtpButton.isEnabled = !loading
            googleButton.isEnabled = !loading
            if (loading) {
                loginContainer.visibility = View.GONE
                loadingProgressBar.visibility = View.VISIBLE
            } else {
                loginContainer.visibility = View.VISIBLE
                loadingProgressBar.visibility = View.GONE
            }
        }
    }

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
        val user = viewModel.checkUser()
        if (user != null) {
            viewModel.checkIfUserRegistered(user)
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.resetRegistrationState()
    }
}