package com.example.outfy.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun sendOtp(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        verificationId: String,
        code: String,
        onResult: (Boolean, FirebaseUser?) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, auth.currentUser)
                } else {
                    onResult(false, null)
                }
            }
    }

    fun signInWithGoogle(idToken: String, onResult: (Boolean, FirebaseUser?) -> Unit) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, auth.currentUser)
                } else {
                    onResult(false, null)
                }
            }
    }

    fun saveUserToFirestore(user: FirebaseUser, onResult: (Boolean) -> Unit) {
        val userMap = hashMapOf(
            "name" to user.displayName,
            "email" to user.email,
            "profilePhoto" to user.photoUrl.toString(),
            "uid" to user.uid
        )
        db.collection("users").document(user.uid).set(userMap)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun checkUserInFirestore(uid: String, onResult: (Boolean) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onResult(document.exists())
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun signOut() {
        auth.signOut()
    }
}