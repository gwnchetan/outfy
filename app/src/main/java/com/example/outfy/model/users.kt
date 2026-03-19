package com.example.outfy.model

import com.google.firebase.Timestamp

data class Users(
    val uid: String = "",
    val name: String = "",
    val gender: String = "",
    val phone: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val profilePhoto: String? = null,
    val authProvider: String = "",
    val status: String = "active",
    val createdAt: Timestamp? = null
)