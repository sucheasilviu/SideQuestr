package com.example.gps_project.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GalleryItem(
    val questId: String = "", // Added this to cross-reference
    val questName: String = "",
    val imageUrl: String = "",
    val timestamp: Long = 0L
)

class GalleryViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _galleryItems = MutableStateFlow<List<GalleryItem>>(emptyList())
    val galleryItems: StateFlow<List<GalleryItem>> = _galleryItems.asStateFlow()

    // NEW: A simple set of completed quest IDs for the map to read
    private val _completedQuestIds = MutableStateFlow<Set<String>>(emptySet())
    val completedQuestIds: StateFlow<Set<String>> = _completedQuestIds.asStateFlow()

    fun fetchGallery() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("gallery")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    val items = snapshot.documents.mapNotNull { it.toObject(GalleryItem::class.java) }
                    _galleryItems.value = items
                    // Extract just the IDs for fast checking
                    _completedQuestIds.value = items.map { it.questId }.toSet()
                }
            }
    }
}