package com.example.gps_project

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import com.google.maps.android.compose.MapUiSettings
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.gps_project.model.StoreItem
import com.example.gps_project.model.UserProfile
import com.example.gps_project.viewmodel.GalleryViewModel
import com.example.gps_project.viewmodel.ProfileViewModel
import com.example.gps_project.viewmodel.QuestCompletionViewModel
import com.example.gps_project.viewmodel.QuestViewModel
import com.example.gps_project.viewmodel.StoreViewModel
import com.example.gps_project.viewmodel.VerificationState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.io.File
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    // 1. Define your custom palette based on the earthy beach gradient
    private val AppPeach = Color(0xFFE1A36F)
    private val AppSand = Color(0xFFDEC484)
    private val AppCream = Color(0xFFE2D8A5)
    private val AppLightTeal = Color(0xFF6F9F9C)
    private val AppSlateBlue = Color(0xFF577E89)
    private val AppErrorRed = Color(0xFFB00020) // Standard Material Error

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Map the new colors to Material 3 roles
        val customColorScheme = lightColorScheme(
            primary = AppPeach,                 // Main FAB, Map Markers
            onPrimary = Color.White,            // Text/Icons on primary
            primaryContainer = AppLightTeal,    // Top Profile Bubble
            onPrimaryContainer = Color.White,   // Text/Icons on top bubble
            secondaryContainer = AppLightTeal,  // Map and Shop bottom buttons
            onSecondaryContainer = Color.White, // Icons on bottom buttons
            tertiary = AppSand,                 // Currency/Level text accents
            onTertiary = Color.Black,
            surfaceVariant = AppCream,          // Quest cards & Store items background
            onSurfaceVariant = AppSlateBlue,    // Text on the cream cards (high contrast)
            error = AppErrorRed,
            onError = Color.White
        )

        setContent {
            // 3. Apply the custom color scheme here
            MaterialTheme(colorScheme = customColorScheme) {
                val uiSettings by remember {
                    mutableStateOf(
                        MapUiSettings(
                            zoomControlsEnabled = false,       // Hides the +/- buttons
                            myLocationButtonEnabled = false,   // Hides the top-right center button
                            mapToolbarEnabled = false          // Hides the tiny navigation toolbar that appears when you click a marker
                        )
                    )
                }
                val context = LocalContext.current
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                // --- VIEWMODELS & UI STATE ---
                val profileViewModel: ProfileViewModel = viewModel()
                val currentUserProfile by profileViewModel.currentUserProfile.collectAsState()

                val questViewModel: QuestViewModel = viewModel()
                val myQuests by questViewModel.quests.collectAsState()

                val questCompletionViewModel: QuestCompletionViewModel = viewModel()
                val galleryViewModel: GalleryViewModel = viewModel()
                val storeViewModel: StoreViewModel = viewModel()

                val completedQuestIds by galleryViewModel.completedQuestIds.collectAsState()

                var showAuthScreen by remember { mutableStateOf(false) }
                var showSettingsMenu by remember { mutableStateOf(false) }
                var showFriendsScreen by remember { mutableStateOf(false) }
                var showQuestsScreen by remember { mutableStateOf(false) }
                var showGalleryScreen by remember { mutableStateOf(false) }
                var showStoreScreen by remember { mutableStateOf(false) }
                var selectedQuestForPopUp by remember { mutableStateOf<Quest?>(null) }

                var hasLocationPermission by remember { mutableStateOf(false) }

                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(40.7829, -73.9654), 12f)
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA
                        )
                    )
                    if (profileViewModel.isUserLoggedIn) {
                        galleryViewModel.fetchGallery()
                    }
                }

                LaunchedEffect(hasLocationPermission) {
                    if (hasLocationPermission) {
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val userLatLng = LatLng(location.latitude, location.longitude)
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatLng, 15f)
                            }
                        }
                    }
                }

                // --- MAIN LAYOUT CONTAINER ---
                Box(modifier = Modifier.fillMaxSize()) {

                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = uiSettings,
                        // Keep lowering this bottom number to push the logo further down!
                        contentPadding = PaddingValues(bottom = 750.dp, top = 100.dp),
                        googleMapOptionsFactory = {
                            com.google.android.gms.maps.GoogleMapOptions().mapId(BuildConfig.MAP_ID)
                        },
                        properties = MapProperties(
                            isMyLocationEnabled = hasLocationPermission
                        )
                    ) {
                        // Custom Marker UI rendering
                        myQuests.forEach { quest ->
                            val isCompleted = completedQuestIds.contains(quest.id)

                            // Use a stable hash of the ID to assign a shape, ensuring it doesn't flicker when the map moves!
                            val shapeIndex = abs(quest.id.hashCode()) % 3
                            val questIcon = when (shapeIndex) {
                                0 -> Icons.Default.Star
                                1 -> Icons.Default.Favorite
                                else -> DiamondIcon
                            }

                            MarkerComposable(
                                state = MarkerState(position = quest.location),
                                onClick = {
                                    selectedQuestForPopUp = quest
                                    true
                                }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isCompleted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                                    border = BorderStroke(
                                        width = 2.dp,
                                        color = if (isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shadowElevation = 8.dp
                                ) {
                                    Box(
                                        modifier = Modifier.padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCompleted) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Completed",
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = questIcon,
                                                contentDescription = "Reward",
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    TopProfileBubble(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        userProfile = currentUserProfile,
                        onProfileClick = {
                            if (profileViewModel.isUserLoggedIn) {
                                showSettingsMenu = true
                            } else {
                                showAuthScreen = true
                            }
                        },
                        onFriendsClick = {
                            if (profileViewModel.isUserLoggedIn) {
                                showFriendsScreen = true
                            } else {
                                showAuthScreen = true
                            }
                        }
                    )

                    BottomActionButtons(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                        onLeftClick = { showQuestsScreen = true },
                        onCenterClick = {
                            if (profileViewModel.isUserLoggedIn) {
                                showGalleryScreen = true
                            } else {
                                showAuthScreen = true
                            }
                        },
                        onRightClick = {
                            if (profileViewModel.isUserLoggedIn) {
                                showStoreScreen = true
                            } else {
                                showAuthScreen = true
                            }
                        }
                    )

                    // --- OVERLAY SCREENS ---
                    if (showAuthScreen && !profileViewModel.isUserLoggedIn) {
                        AuthScreen(onDismiss = { showAuthScreen = false }, viewModel = profileViewModel)
                    }

                    if (showSettingsMenu && profileViewModel.isUserLoggedIn) {
                        SettingsMenu(onDismiss = { showSettingsMenu = false }, viewModel = profileViewModel, currentProfile = currentUserProfile)
                    }

                    if (showFriendsScreen && profileViewModel.isUserLoggedIn) {
                        FriendsScreen(onDismiss = { showFriendsScreen = false }, viewModel = profileViewModel)
                    }

                    if (showQuestsScreen) {
                        QuestListScreen(onDismiss = { showQuestsScreen = false }, quests = myQuests)
                    }

                    if (showGalleryScreen && profileViewModel.isUserLoggedIn) {
                        GalleryScreen(onDismiss = { showGalleryScreen = false }, viewModel = galleryViewModel)
                    }

                    if (showStoreScreen && profileViewModel.isUserLoggedIn) {
                        StoreScreen(
                            onDismiss = { showStoreScreen = false },
                            viewModel = storeViewModel,
                            userProfile = currentUserProfile
                        )
                    }

                    if (selectedQuestForPopUp != null) {
                        QuestDetailPopUp(
                            quest = selectedQuestForPopUp!!,
                            isCompleted = completedQuestIds.contains(selectedQuestForPopUp!!.id),
                            viewModel = questCompletionViewModel,
                            fusedLocationClient = fusedLocationClient,
                            onDismiss = {
                                selectedQuestForPopUp = null
                                questCompletionViewModel.resetState()
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- UI COMPONENTS ---

@Composable
fun TopProfileBubble(
    modifier: Modifier = Modifier,
    userProfile: UserProfile?,
    onProfileClick: () -> Unit,
    onFriendsClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(54.dp)
            ) {
                if (userProfile?.profileImageUrl != null) {
                    AsyncImage(
                        model = userProfile.profileImageUrl,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = userProfile?.username ?: "Guest",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (userProfile != null) "Level 1 • \uD83E\uDE99 ${userProfile.currency}" else "Log in to play",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (userProfile != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (userProfile != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }

            IconButton(onClick = onFriendsClick) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = "Friends",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun BottomActionButtons(
    modifier: Modifier = Modifier,
    onLeftClick: () -> Unit,
    onCenterClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onLeftClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Default.Map, contentDescription = "Map/Quests", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }

        FloatingActionButton(
            onClick = onCenterClick,
            modifier = Modifier
                .size(96.dp)
                .border(4.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Image, contentDescription = "Gallery", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }

        FilledIconButton(
            onClick = onRightClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = "Shop", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

// --- OVERLAY SCREENS ---

@Composable
fun AuthScreen(onDismiss: () -> Unit, viewModel: ProfileViewModel) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            var isRegistering by remember { mutableStateOf(false) }
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var username by remember { mutableStateOf("") }

            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = if (isRegistering) "Create Account" else "Sign In", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                if (isRegistering) {
                    OutlinedTextField(value = username, onValueChange = {username = it}, label = { Text("Username") })
                }

                OutlinedTextField(value = email, onValueChange = {email = it}, label = { Text("Email") })
                OutlinedTextField(value = password, onValueChange = {password = it}, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())

                Button(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    onClick = {
                        if (isRegistering) {
                            viewModel.register(email, password, username)
                        } else {
                            viewModel.login(email, password)
                        }
                    }
                ) {
                    Text(text = if (isRegistering) "Register" else "Login")
                }

                TextButton(onClick = { isRegistering = !isRegistering }) {
                    Text(text = if (isRegistering) "Already have an account? Login" else "New user? Register")
                }
            }
        }
    }
}

@Composable
fun SettingsMenu(onDismiss: () -> Unit, viewModel: ProfileViewModel, currentProfile: UserProfile?) {
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadProfilePicture(uri)
        }
    }

    var newUsername by remember { mutableStateOf("") }
    var isEditingName by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentHeight(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Logged in as: ${currentProfile?.username}", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change Profile Picture")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isEditingName) {
                    OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("New Username") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (newUsername.isNotBlank()) {
                                viewModel.updateUsername(newUsername)
                                isEditingName = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Username")
                    }
                } else {
                    Button(onClick = { isEditingName = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Change Username")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.logout(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            }
        }
    }
}

@Composable
fun FriendsScreen(onDismiss: () -> Unit, viewModel: ProfileViewModel) {
    val searchResults by viewModel.searchResults.collectAsState()
    val pendingRequests by viewModel.pendingRequests.collectAsState()
    val myFriends by viewModel.myFriends.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // UPDATED: Matches the QuestListScreen sizing and shape exactly
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("Friends Hub", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; viewModel.searchUsers(it) },
                    label = { Text("Search users to add...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (searchResults.isNotEmpty()) {
                    Text("Search Results", style = MaterialTheme.typography.titleMedium)
                    searchResults.forEach { user ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(user.username, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { viewModel.sendFriendRequest(user.uid, user.username); searchQuery = ""; viewModel.searchUsers("") }) {
                                Text("Send Request")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text("Friend Requests", style = MaterialTheme.typography.titleMedium)
                if (pendingRequests.isNotEmpty()) {
                    pendingRequests.forEach { request ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${request["fromUsername"]} wants to connect!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { viewModel.acceptFriendRequest(request["id"].toString(), request["fromUid"].toString(), request["fromUsername"].toString()) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                            ) {
                                Text("Accept")
                            }
                        }
                    }
                } else {
                    Text("No pending requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("My Friends", style = MaterialTheme.typography.titleMedium)
                if (myFriends.isNotEmpty()) {
                    myFriends.forEach { friend ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(friend["username"].toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Text("Your friends list is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun QuestListScreen(onDismiss: () -> Unit, quests: List<Quest>) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            var searchQuery by remember { mutableStateOf("") }
            val filteredQuests = quests.filter { quest ->
                quest.name.contains(searchQuery, ignoreCase = true) || quest.details.contains(searchQuery, ignoreCase = true)
            }

            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("Quest Board", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search quests...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (filteredQuests.isEmpty()) {
                        item { Text("No quests found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                    } else {
                        items(filteredQuests) { quest -> QuestCard(quest = quest) }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestCard(quest: Quest) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(quest.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(quest.details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun GalleryScreen(onDismiss: () -> Unit, viewModel: GalleryViewModel) {
    val galleryItems by viewModel.galleryItems.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchGallery()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("My Adventure Gallery", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (galleryItems.isEmpty()) {
                    Text("No completed quests yet. Go explore!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(galleryItems) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column {
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                                            .data(item.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = item.questName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                        // Show a colored box and an error icon if the link is dead
                                        error = rememberVectorPainter(Icons.Default.Image),
                                        fallback = rememberVectorPainter(Icons.Default.Image)
                                    )
                                    Text(
                                        text = item.questName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoreScreen(
    onDismiss: () -> Unit,
    viewModel: StoreViewModel,
    userProfile: UserProfile?
) {
    val storeItems by viewModel.storeItems.collectAsState()
    val context = LocalContext.current

    var transactionMessage by remember { mutableStateOf<String?>(null) }
    var purchasedCode by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rewards Store", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "\uD83E\uDE99 ${userProfile?.currency ?: 0}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (transactionMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (purchasedCode != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(transactionMessage!!, fontWeight = FontWeight.Bold)
                            if (purchasedCode != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Your Code: $purchasedCode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (storeItems.isEmpty()) {
                        item { Text("No items currently available.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(storeItems) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(item.information, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Button(
                                        onClick = {
                                            viewModel.purchaseItem(
                                                item = item,
                                                onSuccess = { code ->
                                                    transactionMessage = "Purchase successful!"
                                                    purchasedCode = code
                                                },
                                                onError = { error ->
                                                    transactionMessage = error
                                                    purchasedCode = null
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("\uD83E\uDE99 ${item.price}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- AI CAMERA VERIFICATION POP-UP WITH GEOFENCE & COMPLETION LOCK ---

@SuppressLint("MissingPermission")
@Composable
fun QuestDetailPopUp(
    quest: Quest,
    isCompleted: Boolean,
    viewModel: QuestCompletionViewModel,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val verificationState by viewModel.verificationState.collectAsState()

    val tempImageUri = remember {
        val tempFile = File.createTempFile("quest_image", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            try {
                val inputStream = context.contentResolver.openInputStream(tempImageUri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val maxDim = 1024
                    val scale = maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)

                    val finalBitmap = if (scale < 1.0) {
                        android.graphics.Bitmap.createScaledBitmap(
                            originalBitmap,
                            (originalBitmap.width * scale).toInt(),
                            (originalBitmap.height * scale).toInt(),
                            true
                        )
                    } else {
                        originalBitmap
                    }

                    viewModel.verifyAndCompleteQuest(finalBitmap, tempImageUri, quest)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(quest.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Reward: \uD83E\uDE99 ${quest.reward}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(quest.details, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(24.dp))

                if (isCompleted) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quest Already Completed!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                    return@Column
                }

                when (verificationState) {
                    is VerificationState.Idle -> {
                        Button(
                            onClick = {
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    if (location != null) {
                                        val results = FloatArray(1)
                                        android.location.Location.distanceBetween(
                                            location.latitude, location.longitude,
                                            quest.location.latitude, quest.location.longitude,
                                            results
                                        )
                                        val distanceInMeters = results[0]

                                        if (distanceInMeters <= 300f) {
                                            cameraLauncher.launch(tempImageUri)
                                        } else {
                                            val dist = distanceInMeters.toInt()
                                            viewModel.setLocationError("You are ${dist}m away! Get within 100m to take the photo.")
                                        }
                                    } else {
                                        viewModel.setLocationError("Waiting for GPS... Make sure your location is turned on.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Photo to Complete")
                        }
                    }
                    is VerificationState.Analyzing -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("AI is analyzing your photo...")
                    }
                    is VerificationState.Uploading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Success! Saving to your gallery...")
                    }
                    is VerificationState.Success -> {
                        Text("Quest Completed!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text("Awesome!")
                        }
                    }
                    is VerificationState.Error -> {
                        val errorMessage = (verificationState as VerificationState.Error).message
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.resetState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            }
        }
    }
}

// --- CUSTOM ICONS ---

// The Diamond shape doesn't exist in standard Material Icons, so we draw it here!
val DiamondIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Diamond",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            pathData = PathBuilder().apply {
                moveTo(12f, 2f)
                lineTo(22f, 12f)
                lineTo(12f, 22f)
                lineTo(2f, 12f)
                close()
            }.nodes, // <--- CHANGED THIS LINE
            fill = SolidColor(Color.Black)
        )
    }.build()