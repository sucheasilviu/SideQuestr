package com.example.gps_project.viewmodel

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.gps_project.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileViewModel : ViewModel() {

    // Initialize the default Firebase services
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // --- Tracker variables for our Firebase Listeners ---
    private var profileListener: ListenerRegistration? = null
    private var pendingRequestsListener: ListenerRegistration? = null
    private var friendsListener: ListenerRegistration? = null

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserProfile>>(emptyList())
    val searchResults: StateFlow<List<UserProfile>> = _searchResults.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingRequests: StateFlow<List<Map<String, Any>>> = _pendingRequests.asStateFlow()

    private val _myFriends = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val myFriends: StateFlow<List<Map<String, Any>>> = _myFriends.asStateFlow()

    var isUserLoggedIn by mutableStateOf(auth.currentUser != null)
        private set

    init {
        auth.currentUser?.let {
            fetchUserProfile(it.uid)
            startListeningForFriends(it.uid)
        }
    }

    private fun fetchUserProfile(uid: String) {
        // Cancel the old listener before starting a new one
        profileListener?.remove()

        profileListener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                println("Error fetching profile: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                _currentUserProfile.value = profile
            } else {
                // If the login works but the database document is missing, sign them out
                println("User document missing in Firestore. Logging out.")
                logout()
            }
        }
    }

    // --- AUTHENTICATION ---
    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            println("Error: Email and password cannot be empty")
            return
        }

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                isUserLoggedIn = true
                result.user?.let { user ->
                    fetchUserProfile(user.uid)
                    startListeningForFriends(user.uid)
                }
            }
            .addOnFailureListener { exception ->
                println("Login failed: ${exception.message}")
            }
    }

    fun register(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            println("Error: Fields cannot be empty")
            return
        }

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                isUserLoggedIn = true
                result.user?.let { user ->
                    val newUser = UserProfile(uid = user.uid, username = username.trim())
                    db.collection("users").document(user.uid).set(newUser).addOnSuccessListener {
                        _currentUserProfile.value = newUser
                    }
                    startListeningForFriends(user.uid)
                }
            }
            .addOnFailureListener { exception ->
                println("Registration failed: ${exception.message}")
            }
    }

    fun logout() {
        auth.signOut()
        isUserLoggedIn = false

        // 1. Kill the Firebase listeners
        profileListener?.remove()
        pendingRequestsListener?.remove()
        friendsListener?.remove()

        // 2. Wipe the UI data clean so the next user starts fresh
        _currentUserProfile.value = null
        _pendingRequests.value = emptyList()
        _myFriends.value = emptyList()
        _searchResults.value = emptyList()
    }

    // --- EDIT PROFILE (USERNAME & PICTURE) ---

    fun updateUsername(newName: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).update("username", newName).addOnSuccessListener {
            _currentUserProfile.value = _currentUserProfile.value?.copy(username = newName)
        }
    }

    fun uploadProfilePicture(imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val imageRef = storage.reference.child("profile_pictures/$uid.jpg")

        imageRef.putFile(imageUri).addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                db.collection("users").document(uid).update("profileImageUrl", downloadUrl.toString())
                    .addOnSuccessListener {
                        _currentUserProfile.value = _currentUserProfile.value?.copy(profileImageUrl = downloadUrl.toString())
                    }
            }
        }
    }

    // --- FRIENDS & SEARCH ---

    private fun startListeningForFriends(uid: String) {
        // Cancel any old ghost listeners before starting new ones!
        pendingRequestsListener?.remove()
        friendsListener?.remove()

        // 1. Listen for Incoming Friend Requests
        pendingRequestsListener = db.collection("friendRequests")
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snapshot, _ ->
                val requests = snapshot?.documents?.map { doc ->
                    doc.data?.plus("id" to doc.id) ?: emptyMap()
                } ?: emptyList()
                _pendingRequests.value = requests
            }

        // 2. Listen for Accepted Friends
        friendsListener = db.collection("users").document(uid).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                val friends = snapshot?.documents?.map { doc ->
                    doc.data ?: emptyMap()
                } ?: emptyList()
                _myFriends.value = friends
            }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        db.collection("users")
            .whereGreaterThanOrEqualTo("username", query)
            .whereLessThanOrEqualTo("username", query + "\uf8ff")
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.toObjects(UserProfile::class.java)
                _searchResults.value = results.filter { it.uid != auth.currentUser?.uid }
            }
    }

    fun sendFriendRequest(targetUid: String, targetUsername: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentUsername = _currentUserProfile.value?.username ?: "Guest"

        val request = hashMapOf(
            "fromUid" to currentUid,
            "fromUsername" to currentUsername,
            "toUid" to targetUid,
            "toUsername" to targetUsername,
            "status" to "PENDING",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("friendRequests").add(request)
    }

    fun acceptFriendRequest(requestId: String, fromUid: String, fromUsername: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val currentUsername = _currentUserProfile.value?.username ?: "Guest"

        // 1. Mark the request as ACCEPTED
        db.collection("friendRequests").document(requestId).update("status", "ACCEPTED")

        // 2. Add them to YOUR friends list
        db.collection("users").document(currentUid).collection("friends").document(fromUid)
            .set(hashMapOf("uid" to fromUid, "username" to fromUsername))

        // 3. Add YOU to THEIR friends list
        db.collection("users").document(fromUid).collection("friends").document(currentUid)
            .set(hashMapOf("uid" to currentUid, "username" to currentUsername))
    }
}