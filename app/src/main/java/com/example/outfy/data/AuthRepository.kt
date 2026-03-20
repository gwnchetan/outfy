package com.example.outfy.data

import android.app.Activity
import com.google.firebase.auth.*
import com.google.firebase.firestore.FieldValue
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
        signInWithCredential(credential, onResult)
    }

    fun signInWithCredential(
        credential: PhoneAuthCredential,
        onResult: (Boolean, FirebaseUser?) -> Unit
    ) {
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

    fun saveUserToFirestore(
        uid: String,
        name: String,
        email: String?,
        dob: String?,
        gender: String,
        phone: String?,
        authProvider: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val userMap = mapOf(
            "uid"          to uid,
            "name"         to name,
            "gender"       to gender,
            "phone"        to phone,
            "email"        to email,
            "dob"          to dob,
            "profilePhoto" to null,
            "authProvider" to authProvider,
            "status"       to "active",
            "createdAt"    to FieldValue.serverTimestamp()
        )

        if (email != null) {
            db.collection("uniqueIndex").document("email_$email").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("uid") != uid) {
                        onResult(false, "This email is already linked to another account")
                    } else {
                        saveWithIndex(uid, email, phone, userMap, onResult)
                    }
                }
                .addOnFailureListener {
                    onResult(false, "Network error. Try again.")
                }
        } else {
            saveWithIndex(uid, null, phone, userMap, onResult)
        }
    }

    private fun saveWithIndex(
        uid: String,
        email: String?,
        phone: String?,
        userMap: Map<String, Any?>,
        onResult: (Boolean, String?) -> Unit
    ) {
        val batch = db.batch()

        batch.set(db.collection("users").document(uid), userMap)

        if (email != null) {
            batch.set(
                db.collection("uniqueIndex").document("email_$email"),
                mapOf("uid" to uid)
            )
        }

        if (phone != null) {
            batch.set(
                db.collection("uniqueIndex").document("phone_$phone"),
                mapOf("uid" to uid)
            )
        }

        batch.commit()
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun checkUserInFirestore(uid: String, onResult: (Boolean?, String?) -> Unit) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                onResult(document.exists(), null)
            }
            .addOnFailureListener { e ->
                onResult(null, e.message)
            }
    }

    fun checkOtpLimit(phone: String, onResult: (allowed: Boolean, message: String?) -> Unit) {
        val doc = db.collection("otpAttempts").document(phone)
        doc.get()
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                if (!snapshot.exists()) {
                    doc.set(mapOf("count" to 1, "lastAttemptTime" to now))
                    onResult(true, null)
                } else {
                    val count = snapshot.getLong("count") ?: 0
                    val lastAttemptTime = snapshot.getLong("lastAttemptTime") ?: 0

                    if (now - lastAttemptTime >= twentyFourHours) {
                        doc.set(mapOf("count" to 1, "lastAttemptTime" to now))
                        onResult(true, null)
                    } else if (count >= 3) {
                        val remainingMs = twentyFourHours - (now - lastAttemptTime)
                        val remainingHours = remainingMs / (1000 * 60 * 60)
                        val remainingMinutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
                        onResult(false, "Too many attempts. Try again in ${remainingHours}h ${remainingMinutes}m")
                    } else {
                        doc.update(mapOf("count" to count + 1, "lastAttemptTime" to now))
                        onResult(true, null)
                    }
                }
            }
            .addOnFailureListener {
                onResult(true, null)
            }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun signOut(
        googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient? = null,
        onComplete: () -> Unit
    ) {
        auth.signOut()
        if (googleSignInClient != null) {
            googleSignInClient.signOut().addOnCompleteListener { onComplete() }
        } else {
            onComplete()
        }
    }
    }
