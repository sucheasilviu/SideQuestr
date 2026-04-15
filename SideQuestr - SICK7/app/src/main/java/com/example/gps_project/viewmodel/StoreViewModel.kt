package com.example.gps_project.viewmodel

import androidx.lifecycle.ViewModel
import com.example.gps_project.model.StoreItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StoreViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _storeItems = MutableStateFlow<List<StoreItem>>(emptyList())
    val storeItems: StateFlow<List<StoreItem>> = _storeItems.asStateFlow()

    init {
        fetchStoreItems()
    }

    private fun fetchStoreItems() {
        // Listens to the global "store" collection
        db.collection("store").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                _storeItems.value = snapshot.documents.mapNotNull { it.toObject(StoreItem::class.java) }
            }
        }
    }

    // Securely buys the item using a Firestore Transaction to prevent negative balances
    fun purchaseItem(item: StoreItem, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onError("You must be logged in.")
            return
        }

        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            val currentCurrency = userSnapshot.getLong("currency") ?: 0

            if (currentCurrency >= item.price) {
                // 1. Deduct the currency
                transaction.update(userRef, "currency", FieldValue.increment(-item.price.toLong()))

                // 2. Generate a random discount code (or fetch a real one from the item if you prefer)
                val generatedCode = "${item.name.take(4).uppercase()}-${(1000..9999).random()}"

                // 3. Save it to the user's inventory
                val inventoryRef = userRef.collection("inventory").document()
                val inventoryItem = hashMapOf(
                    "itemName" to item.name,
                    "code" to generatedCode,
                    "timestamp" to System.currentTimeMillis()
                )
                transaction.set(inventoryRef, inventoryItem)

                generatedCode // Return this to the success listener
            } else {
                throw Exception("Not enough coins! You need 🪙 ${item.price - currentCurrency} more.")
            }
        }.addOnSuccessListener { code ->
            onSuccess(code as String)
        }.addOnFailureListener { e ->
            onError(e.message ?: "Transaction failed.")
        }
    }
}