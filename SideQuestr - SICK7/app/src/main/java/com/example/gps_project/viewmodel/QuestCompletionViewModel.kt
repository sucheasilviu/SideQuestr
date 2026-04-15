package com.example.gps_project.viewmodel

import android.graphics.Bitmap
import com.example.gps_project.BuildConfig
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gps_project.Quest
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class QuestCompletionViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // IMPORTANT: Replace with your actual key from Google AI Studio!
    private val geminiModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // UI States: IDLE -> ANALYZING -> UPLOADING -> SUCCESS or ERROR
    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    fun verifyAndCompleteQuest(bitmap: Bitmap, uri: Uri, quest: Quest) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _verificationState.value = VerificationState.Error("User not logged in.")
            return
        }

        viewModelScope.launch {
            try {
                _verificationState.value = VerificationState.Analyzing

                // 1. AI VERIFICATION
                val prompt = """
                    Analyze this image. Does it depict or correspond to the following quest description?
                    Quest: ${quest.name}
                    Description: ${quest.details}
                    
                    If it matches, reply strictly with the word "VALID". 
                    If it does not match, reply with "INVALID:" followed by a short reason why.
                """.trimIndent()

                val response = geminiModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val aiVerdict = response.text?.trim() ?: "INVALID: No response from AI"

                if (aiVerdict.startsWith("VALID")) {
                    // 2. UPLOAD TO FIREBASE STORAGE
                    _verificationState.value = VerificationState.Uploading
                    val imageRef = storage.reference.child("galleries/$uid/${UUID.randomUUID()}.jpg")
                    imageRef.putFile(uri).await()
                    val downloadUrl = imageRef.downloadUrl.await().toString()

                    // 3. SAVE TO FIRESTORE GALLERY & REWARD USER WITH CURRENCY
                    val galleryItem = hashMapOf(
                        "questId" to quest.id, // Used by the map to turn the marker green
                        "questName" to quest.name,
                        "imageUrl" to downloadUrl,
                        "timestamp" to System.currentTimeMillis()
                    )

                    val userRef = db.collection("users").document(uid)

                    // We use a Batch to ensure both the gallery is updated AND the money is added at the exact same time
                    db.runBatch { batch ->
                        val newGalleryRef = userRef.collection("gallery").document()
                        batch.set(newGalleryRef, galleryItem)

                        // Safely add the quest reward to the user's total currency
                        batch.update(userRef, "currency", FieldValue.increment(quest.reward.toLong()))
                    }.await()

                    _verificationState.value = VerificationState.Success
                } else {
                    // AI rejected the photo
                    _verificationState.value = VerificationState.Error(aiVerdict)
                }

            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    // Helper function used by the UI to block the camera if the user is further than 100 meters away
    fun setLocationError(errorMessage: String) {
        _verificationState.value = VerificationState.Error(errorMessage)
    }

    fun resetState() {
        _verificationState.value = VerificationState.Idle
    }
}

sealed class VerificationState {
    object Idle : VerificationState()
    object Analyzing : VerificationState()
    object Uploading : VerificationState()
    object Success : VerificationState()
    data class Error(val message: String) : VerificationState()
}