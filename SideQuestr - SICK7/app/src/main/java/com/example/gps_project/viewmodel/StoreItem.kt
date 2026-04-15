package com.example.gps_project.model

import com.google.firebase.firestore.DocumentId

data class StoreItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val information: String = "",
    val price: Int = 0
)