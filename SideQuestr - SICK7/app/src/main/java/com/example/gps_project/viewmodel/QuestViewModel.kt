package com.example.gps_project.viewmodel

import androidx.lifecycle.ViewModel
import com.example.gps_project.Quest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuestViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    // Holds the live list of quests
    private val _quests = MutableStateFlow<List<Quest>>(emptyList())
    val quests: StateFlow<List<Quest>> = _quests.asStateFlow()

    init {
        // Start listening to the database as soon as the ViewModel is created
        listenForQuests()
    }

    private fun listenForQuests() {
        // SnapshotListener keeps an open connection.
        // If anyone adds a quest to the DB, this triggers instantly.
        db.collection("quests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Error fetching quests: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // Convert Firestore documents directly into our Quest data class
                    val fetchedQuests = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Quest::class.java)
                    }
                    _quests.value = fetchedQuests
                }
            }
    }

    // Helper function so you can easily add quests later (e.g., from a "Create Quest" screen)
    fun addQuest(name: String, details: String, latitude: Double, longitude: Double) {
        val newQuest = Quest(name = name, details = details, latitude = latitude, longitude = longitude)
        db.collection("quests").add(newQuest)
    }
}