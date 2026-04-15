package com.example.gps_project

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

data class Quest(
    @DocumentId val id: String = "",
    val name: String = "",
    val details: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val reward: Int = 100 // <--- NEW FIELD (Defaults to 100)
) {
    @get:Exclude
    val location: LatLng
        get() = LatLng(latitude, longitude)
}