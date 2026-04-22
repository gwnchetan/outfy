package com.example.outfy.data

import android.app.Activity
import com.example.outfy.model.UserAddress
import com.example.outfy.model.Users
import com.example.outfy.model.isComplete
import com.google.firebase.auth.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale
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

    private fun normalizeEmail(email: String?): String? =
        email?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotEmpty() }

    private fun normalizePhone(phone: String?): String? =
        phone?.trim()?.takeIf { it.isNotEmpty() }

    private fun getIndexedUid(indexKey: String, onResult: (String?, String?) -> Unit) {
        db.collection("uniqueIndex").document(indexKey).get()
            .addOnSuccessListener { doc ->
                onResult(doc.getString("uid"), null)
            }
            .addOnFailureListener { e ->
                onResult(null, e.message)
            }
    }

    fun findUidByEmail(email: String?, onResult: (String?, String?) -> Unit) {
        val normalizedEmail = normalizeEmail(email)
        if (normalizedEmail == null) {
            onResult(null, null)
            return
        }

        getIndexedUid("email_$normalizedEmail", onResult)
    }

    private fun checkRegistrationConflicts(
        uid: String,
        email: String?,
        phone: String?,
        onResult: (String?) -> Unit
    ) {
        val normalizedEmail = normalizeEmail(email)
        val normalizedPhone = normalizePhone(phone)

        fun checkPhoneConflict() {
            if (normalizedPhone == null) {
                onResult(null)
                return
            }

            getIndexedUid("phone_$normalizedPhone") { existingUid, error ->
                when {
                    error != null -> onResult("Network error. Try again.")
                    existingUid != null && existingUid != uid ->
                        onResult("This phone number is already linked to another account")
                    else -> onResult(null)
                }
            }
        }

        if (normalizedEmail == null) {
            checkPhoneConflict()
            return
        }

        getIndexedUid("email_$normalizedEmail") { existingUid, error ->
            when {
                error != null -> onResult("Network error. Try again.")
                existingUid != null && existingUid != uid ->
                    onResult("This email is already linked to another account")
                else -> checkPhoneConflict()
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
        val normalizedEmail = normalizeEmail(email)
        val normalizedPhone = normalizePhone(phone)
        val userMap = mapOf(
            "uid"          to uid,
            "name"         to name,
            "gender"       to gender,
            "phone"        to normalizedPhone,
            "email"        to normalizedEmail,
            "dob"          to dob,
            "profilePhoto" to null,
            "authProvider" to authProvider.lowercase(Locale.US),
            "status"       to "active",
            "createdAt"    to FieldValue.serverTimestamp()
        )

        checkRegistrationConflicts(uid, normalizedEmail, normalizedPhone) { conflictMessage ->
            if (conflictMessage != null) {
                onResult(false, conflictMessage)
            } else {
                saveWithIndex(uid, normalizedEmail, normalizedPhone, userMap, onResult)
            }
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

    suspend fun getUserProfile(uid: String): Users? {
        return db.collection("users").document(uid).get().await().toObject(Users::class.java)
    }

    suspend fun updateUserProfile(
        uid: String,
        name: String,
        gender: String,
        dob: String?
    ): Users? {
        val normalizedName = name.trim()
        val normalizedGender = gender.trim()
        val normalizedDob = dob?.trim()?.takeIf { it.isNotEmpty() }

        if (normalizedName.isEmpty()) {
            throw IllegalStateException("Full name is required.")
        }

        if (normalizedGender.isEmpty()) {
            throw IllegalStateException("Select a gender.")
        }

        db.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "name" to normalizedName,
                    "gender" to normalizedGender,
                    "dob" to normalizedDob,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        auth.currentUser?.updateProfile(
            UserProfileChangeRequest.Builder()
                .setDisplayName(normalizedName)
                .build()
        )?.await()

        return getUserProfile(uid)
    }

    suspend fun updateDefaultAddress(
        uid: String,
        address: UserAddress
    ): Users? {
        val normalizedAddress = UserAddress(
            fullName = address.fullName.trim(),
            phone = address.phone.trim(),
            line1 = address.line1.trim(),
            line2 = address.line2.trim(),
            city = address.city.trim(),
            state = address.state.trim(),
            pincode = address.pincode.trim(),
            label = address.label.trim().ifEmpty { "HOME" }.uppercase(Locale.US)
        )

        if (!normalizedAddress.isComplete()) {
            throw IllegalStateException("Complete the address before saving.")
        }

        db.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "defaultAddress" to mapOf(
                        "fullName" to normalizedAddress.fullName,
                        "phone" to normalizedAddress.phone,
                        "line1" to normalizedAddress.line1,
                        "line2" to normalizedAddress.line2,
                        "city" to normalizedAddress.city,
                        "state" to normalizedAddress.state,
                        "pincode" to normalizedAddress.pincode,
                        "label" to normalizedAddress.label
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()

        return getUserProfile(uid)
    }

    fun getCurrentProviderIds(): List<String> =
        auth.currentUser?.providerData
            ?.mapNotNull { providerInfo ->
                providerInfo.providerId.takeUnless { it == FirebaseAuthProvider.PROVIDER_ID }
            }
            ?.distinct()
            .orEmpty()

    suspend fun sendPasswordResetForCurrentUser(): String {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in again.")
        val email = normalizeEmail(user.email)
            ?: throw IllegalStateException("No email is linked to this account.")
        val providerIds = getCurrentProviderIds()

        return when {
            EmailAuthProvider.PROVIDER_ID in providerIds -> {
                auth.sendPasswordResetEmail(email).await()
                "Password reset email sent to $email"
            }
            GoogleAuthProvider.PROVIDER_ID in providerIds ->
                throw IllegalStateException("This account uses Google sign-in. Change your password from your Google account.")
            PhoneAuthProvider.PROVIDER_ID in providerIds ->
                throw IllegalStateException("This account uses OTP login, so there is no password to change here.")
            else ->
                throw IllegalStateException("Password reset is not available for this sign-in method.")
        }
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
