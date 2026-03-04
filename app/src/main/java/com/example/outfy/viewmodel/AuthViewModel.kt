package com.example.outfy.viewmodel

import android.app.Activity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.outfy.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    val verificationId = MutableLiveData<String>()
    val authResult = MutableLiveData<FirebaseUser?>()
    val isUserRegistered = MutableLiveData<Boolean>()
    val errorMessage = MutableLiveData<String>()

    fun sendOtp(activity: Activity, phoneNumber: String) {

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {}

            override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                errorMessage.value = e.message
            }

            override fun onCodeSent(
                id: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId.value = id
            }
        }

        repository.sendOtp(activity, phoneNumber, callbacks)
    }

    fun verifyOtp(id: String, code: String) {
        repository.verifyOtp(id, code) { success, user ->
            if (success) {
                authResult.value = user
            } else {
                errorMessage.value = "Invalid OTP"
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        repository.signInWithGoogle(idToken) { success, user ->
            if (success && user != null) {
                checkIfUserRegistered(user)
            } else {
                errorMessage.value = "Google Sign-In Failed"
            }
        }
    }

    fun checkIfUserRegistered(user: FirebaseUser) {
        repository.checkUserInFirestore(user.uid) { registered ->
            if (!registered) {
                repository.saveUserToFirestore(user) { saved ->
                    if (saved) {
                        authResult.value = user
                        isUserRegistered.value = true
                    } else {
                        errorMessage.value = "Failed to save user data"
                    }
                }
            } else {
                authResult.value = user
                isUserRegistered.value = true
            }
        }
    }

    fun checkUser(): FirebaseUser? {
        return repository.getCurrentUser()
    }

    fun logout() {
        repository.signOut()
    }
}