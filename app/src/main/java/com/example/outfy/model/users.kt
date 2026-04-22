package com.example.outfy.model

import com.google.firebase.Timestamp

data class UserAddress(
    val fullName: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    val label: String = "HOME"
)

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
    val createdAt: Timestamp? = null,
    val defaultAddress: UserAddress? = null
)

fun UserAddress.isComplete(): Boolean =
    fullName.isNotBlank() &&
        phone.isNotBlank() &&
        line1.isNotBlank() &&
        city.isNotBlank() &&
        state.isNotBlank() &&
        pincode.isNotBlank()

fun UserAddress.streetLine(): String =
    listOf(line1.trim(), line2.trim())
        .filter { it.isNotEmpty() }
        .joinToString(", ")

fun UserAddress.cityLine(): String =
    listOf(city.trim(), state.trim(), pincode.trim())
        .filter { it.isNotEmpty() }
        .joinToString(", ")
