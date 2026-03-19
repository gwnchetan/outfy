package com.example.outfy.data

import android.app.Activity
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
        onResult: (Boolean, String?) -> Unit  // Boolean = success, String = error message
    ) {
        // ── Step 1: Build user map from finalized schema ───────────
        val currentUser = auth.currentUser

        val userMap = mapOf(
            "uid"          to uid,
            "name"         to name,
            "gender"       to gender,
            "phone"        to (currentUser?.phoneNumber ?: null),
            "email"        to email,
            "dob"          to dob,
            "profilePhoto" to null,
            "authProvider" to if (currentUser?.phoneNumber != null) "phone" else "google",
            "status"       to "active",
            "createdAt"    to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // ── Step 2: Check email uniqueness BEFORE saving ────────────
        // If email provided, make sure no other user owns it
        if (email != null) {
            db.collection("uniqueIndex").document("email_$email").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists() && doc.getString("uid") != uid) {
                        // Email already belongs to someone else
                        onResult(false, "This email is already linked to another account")
                    } else {
                        saveWithIndex(uid, email, currentUser?.phoneNumber, userMap, onResult)
                    }
                }
                .addOnFailureListener {
                    onResult(false, "Network error. Try again.")
                }
        } else {
            saveWithIndex(uid, null, currentUser?.phoneNumber, userMap, onResult)
        }
    }

    // ── Step 3: Save user + uniqueIndex in one batch ────────────────
// Batch = both writes succeed or both fail. No half-saved data.
    private fun saveWithIndex(
        uid: String,
        email: String?,
        phone: String?,
        userMap: Map<String, Any?>,
        onResult: (Boolean, String?) -> Unit
    ) {
        val batch = db.batch()

        // Write user document
        batch.set(db.collection("users").document(uid), userMap)

        // Write email index if provided
        if (email != null) {
            batch.set(
                db.collection("uniqueIndex").document("email_$email"),
                mapOf("uid" to uid)
            )
        }

        // Write phone index if provided
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
                    // first attempt
                    doc.set(mapOf("count" to 1, "lastAttemptTime" to now))
                    onResult(true, null)
                } else {
                    val count = snapshot.getLong("count") ?: 0
                    val lastAttemptTime = snapshot.getLong("lastAttemptTime") ?: 0

                    if (now - lastAttemptTime >= twentyFourHours) {
                        // 24 hours passed, reset
                        doc.set(mapOf("count" to 1, "lastAttemptTime" to now))
                        onResult(true, null)
                    } else if (count >= 3) {
                        // blocked
                        val remainingMs = twentyFourHours - (now - lastAttemptTime)
                        val remainingHours = remainingMs / (1000 * 60 * 60)
                        val remainingMinutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
                        onResult(false, "Too many attempts. Try again in ${remainingHours}h ${remainingMinutes}m")
                    } else {
                        // increment count
                        doc.update(mapOf("count" to count + 1, "lastAttemptTime" to now))
                        onResult(true, null)
                    }
                }
            }
            .addOnFailureListener {
                // if check fails allow the attempt to not block legitimate users
                onResult(true, null)
            }
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun signOut() {
        auth.signOut()
    }
}
