package com.example.outfy.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.outfy.data.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _verificationId = MutableLiveData<String?>()
    val verificationId: LiveData<String?> = _verificationId

    private val _authResult = MutableLiveData<FirebaseUser?>()
    val authResult: LiveData<FirebaseUser?> = _authResult

    private val _isUserRegistered = MutableLiveData<Boolean?>()
    val isUserRegistered: LiveData<Boolean?> = _isUserRegistered

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _registerResult = MutableLiveData<Result<Unit>>()
    val registerResult: LiveData<Result<Unit>> = _registerResult

    fun sendOtp(activity: Activity, phoneNumber: String) {
        _isLoading.value = true
        repository.checkOtpLimit(phoneNumber) { allowed, message ->
            if (!allowed) {
                _isLoading.value = false
                _errorMessage.value = message
                return@checkOtpLimit
            }
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    repository.signInWithCredential(credential) { success, user ->
                        _isLoading.value = false
                        if (success) _authResult.value = user
                        else _errorMessage.value = "Auto verification failed"
                    }
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    _isLoading.value = false
                    _errorMessage.value = e.message
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    _isLoading.value = false
                    _verificationId.value = id
                }
            }
            repository.sendOtp(activity, phoneNumber, callbacks)
        }
    }

    fun verifyOtp(id: String, code: String) {
        _isLoading.value = true
        repository.verifyOtp(id, code) { success, user ->
            _isLoading.value = false
            if (success) _authResult.value = user
            else _errorMessage.value = "Invalid OTP. Please try again."
        }
    }

    fun signInWithGoogle(idToken: String) {
        _isLoading.value = true
        repository.signInWithGoogle(idToken) { success, user ->
            if (success && user != null) {
                repository.checkUserInFirestore(user.uid) { exists, error ->
                    if (error != null) {
                        _isLoading.value = false
                        _errorMessage.value = "Network error. Please try again."
                        return@checkUserInFirestore
                    }
                    if (exists == true) {
                        _isLoading.value = false
                        _authResult.value = user
                        _isUserRegistered.value = true
                    } else {
                        _isLoading.value = false
                        _authResult.value = user
                        _isUserRegistered.value = false
                    }
                }
            } else {
                _isLoading.value = false
                _errorMessage.value = "Google Sign-In Failed"
            }
        }
    }

    fun checkIfUserRegistered(user: FirebaseUser) {
        _isLoading.value = true
        repository.checkUserInFirestore(user.uid) { exists, error ->
            _isLoading.value = false
            if (error != null) {
                _errorMessage.value = "Network error. Please try again."
                return@checkUserInFirestore
            }
            _authResult.value = user
            _isUserRegistered.value = exists
        }
    }

    fun registerUser(
        name: String,
        email: String?,
        dob: String,
        gender: String,
        phone: String?,
        authProvider: String
    ) {
        val uid = repository.getCurrentUser()?.uid
        if (uid == null) {
            _registerResult.value = Result.failure(Exception("Session expired. Login again."))
            return
        }
        repository.saveUserToFirestore(
            uid          = uid,
            name         = name,
            email        = email,
            dob          = dob,
            gender       = gender,
            phone        = phone,
            authProvider = authProvider
        ) { success, error ->
            _registerResult.value = if (success) Result.success(Unit)
            else Result.failure(Exception(error ?: "Registration failed"))
        }
    }

    fun checkUser(): FirebaseUser? = repository.getCurrentUser()
    fun logout(
        googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null,
        onComplete: () -> Unit
    ) {
        repository.signOut(googleSignInClient, onComplete)
    }
    fun resetRegistrationState() { _isUserRegistered.value = null }
    fun resetVerificationId() { _verificationId.value = null }
}