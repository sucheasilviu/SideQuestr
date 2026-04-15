package com.example.gps_project.model

import com.google.firebase.Timestamp

data class FriendRequest(
    val id: String = "",              // The document ID in Firestore
    val fromUid: String = "",         // UID of the person who sent the request
    val fromUsername: String = "",     // To avoid unnecessary extra lookups
    val toUid: String = "",           // UID of the person receiving the request
    val status: RequestStatus = RequestStatus.PENDING,
    val timestamp: Timestamp = Timestamp.now() // For sorting
)

enum class RequestStatus {
    PENDING, ACCEPTED, REJECTED
}