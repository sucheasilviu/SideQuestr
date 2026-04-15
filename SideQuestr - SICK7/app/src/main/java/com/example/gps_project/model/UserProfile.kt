package com.example.gps_project.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val profileImageUrl: String? = null,
    val currency: Int = 0 // <--- NEW FIELD
)