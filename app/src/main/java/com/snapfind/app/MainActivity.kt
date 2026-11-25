package com.snapfind.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private val SnapfindColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color(0xFF171226),
    secondary = Color(0xFFFFC66D),
    background = Color(0xFF0D0E14),
    surface = Color(0xFF171923),
    onSurface = Color(0xFFF3F1FA),
    onBackground = Color(0xFFF3F1FA),
    surfaceVariant = Color(0xFF242737),
    onSurfaceVariant = Color(0xFFB9B7C8)
)

private data class SnapProfile(
    val id: String,
    val displayName: String,
    val username: String,
    val country: String,
    val interests: String,
    val bio: String,
    val isPreview: Boolean = false,
    val isPublished: Boolean = true
)

private enum class AppScreen { EXPLORE, PROFILE, ADD, EDIT, AUTH }

private val SnapfindExecutor = Executors.newFixedThreadPool(3)
private val SnapfindMainHandler = Handler(Looper.getMainLooper())

private fun <T> runInBackground(
    work: () -> T,
    onResult: (T) -> Unit,
    onError: (String) -> Unit
) {
    SnapfindExecutor.execute {
        try {
            val result = work()
            SnapfindMainHandler.post { onResult(result) }
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
            SnapfindMainHandler.post { onError(message) }
        }
    }
}

private val SnapchatUsernamePattern = Regex("^[A-Za-z][A-Za-z0-9._-]{1,13}[A-Za-z0-9]$")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = SnapfindColors) {
                SnapfindApp()
            }
        }
    }
}

@Composable
private fun SnapfindApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("snapfind_local_profile", Context.MODE_PRIVATE)
    }
    val repository = remember(context) { SupabaseRepository(context) }
    var session by remember { mutableStateOf(repository.savedSession()) }
    var authLoading by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var directoryLoading by remember { mutableStateOf(repository.isConfigured) }
    var directoryError by remember { mutableStateOf<String?>(null) }

    var ownProfile by remember {
        mutableStateOf(
            preferences.getString("username", null)?.let { username ->
                SnapProfile(
                    id = "owner",
                    displayName = preferences.getString("display_name", "").orEmpty(),
                    username = username,
                    country = preferences.getString("country", "").orEmpty(),
                    interests = preferences.getString("interests", "").orEmpty(),
                    bio = preferences.getString("bio", "").orEmpty(),
                    isPublished = preferences.getBoolean("published", true)
                )
            }
        )
    }
    var isPublished by remember { mutableStateOf(preferences.getBoolean("published", true)) }
    val profiles = remember {
        mutableStateListOf<SnapProfile>().apply {
            if (!repository.isConfigured) {
                addAll(
                    listOf(
                        SnapProfile("preview-1", "Maya", "maya.frames", "Nigeria", "Photography, Travel", "Capturing small moments and exploring new places.", true),
                        SnapProfile("preview-2", "Tobi", "tobi.codes", "Nigeria", "Technology, Music", "CS student, music lover and curious builder.", true),
                        SnapProfile("preview-3", "Zara", "zara.vibes", "Ghana", "Fashion, Art", "Style, colour and creative conversations.", true),
                        SnapProfile("preview-4", "Noah", "noah.outdoors", "United Kingdom", "Football, Fitness", "Usually outdoors or watching a good match.", true),
                        SnapProfile("preview-5", "Ari", "ari.creates", "Canada", "Design, Gaming", "Making things, learning things, and sharing ideas.", true)
                    )
                )
            }
        }
    }

    LaunchedEffect(ownProfile, isPublished, repository.isConfigured) {
        if (!repository.isConfigured) {
            profiles.removeAll { it.id == "owner" }
            if (ownProfile != null && isPublished) {
                profiles.add(0, ownProfile!!.copy(id = "owner", isPreview = false))
            }
        }
    }

    LaunchedEffect(repository.isConfigured) {
        if (repository.isConfigured) {
            runInBackground(
                work = { repository.validSession() },
                onResult = { restored -> session = restored },
                onError = { message -> directoryError = message }
            )
        }
    }

    LaunchedEffect(repository.isConfigured, session?.userId) {
        if (repository.isConfigured) {
            directoryLoading = true
            directoryError = null
            runInBackground(
                work = {
                    val discovered = repository.fetchPublicProfiles()
                    val mine = session?.let { repository.fetchOwnProfile(it.userId) }
                    discovered to mine
                },
                onResult = { result ->
                    profiles.clear()
                    profiles.addAll(result.first)
                    ownProfile = result.second
                    isPublished = result.second?.isPublished ?: false
                    directoryLoading = false
                },
                onError = { message ->
                    directoryError = message
                    directoryLoading = false
                }
            )
        }
    }

    var screen by remember { mutableStateOf(AppScreen.EXPLORE) }
    var reportTarget by remember { mutableStateOf<SnapProfile?>(null) }
    var selectedReportReason by remember { mutableStateOf("spam") }

    fun persistProfile(profile: SnapProfile) {
        if (repository.isConfigured) {
            val activeSession = session
            if (activeSession == null) {
                screen = AppScreen.AUTH
                authMessage = "Sign in to publish your profile."
                return
            }
            authLoading = true
            runInBackground(
                work = {
                    repository.saveProfile(profile, activeSession.userId)
                    profile.copy(id = activeSession.userId, isPreview = false)
                },
                onResult = { saved ->
                    ownProfile = saved
                    isPublished = saved.isPublished
                    authLoading = false
                    authMessage = null
                    screen = AppScreen.PROFILE
                },
                onError = { message ->
                    authLoading = false
                    authMessage = message
                    screen = AppScreen.EDIT
                }
            )
            return
        }

        val saved = profile.copy(id = "owner", isPreview = false)
        preferences.edit()
            .putString("display_name", saved.displayName)
            .putString("username", saved.username)
            .putString("country", saved.country)
            .putString("interests", saved.interests)
            .putString("bio", saved.bio)
            .putBoolean("published", saved.isPublished)
            .apply()
        ownProfile = saved
        isPublished = saved.isPublished
        screen = AppScreen.PROFILE
    }

    fun copyUsername(profile: SnapProfile) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Snapchat username", profile.username))
        Toast.makeText(context, "@${profile.username} copied", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        containerColor = SnapfindColors.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF151722)) {
                NavigationBarItem(
                    selected = screen == AppScreen.EXPLORE,
                    onClick = { screen = AppScreen.EXPLORE },
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                    label = { Text("Discover") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.PROFILE,
                    onClick = { screen = if (repository.isConfigured && session == null) AppScreen.AUTH else AppScreen.PROFILE },
                    icon = { Icon(Icons.Default.Person, contentDescription = "My listing") },
                    label = { Text("My listing") }
                )
                NavigationBarItem(
                    selected = screen == AppScreen.ADD || screen == AppScreen.EDIT,
                    onClick = {
                        screen = when {
                            repository.isConfigured && session == null -> AppScreen.AUTH
                            ownProfile == null -> AppScreen.ADD
                            else -> AppScreen.EDIT
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add listing") },
                    label = { Text("Publish") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppHeader()
            when (screen) {
                AppScreen.EXPLORE -> ExploreScreen(
                    profiles = profiles,
                    backendConfigured = repository.isConfigured,
                    isLoading = directoryLoading,
                    errorMessage = directoryError,
                    onCopy = ::copyUsername,
                    onReport = { reportTarget = it },
                    onSearch = { query, country ->
                        if (repository.isConfigured) {
                            directoryLoading = true
                            runInBackground(
                                work = { repository.fetchPublicProfiles(query, country) },
                                onResult = { found ->
                                    profiles.clear()
                                    profiles.addAll(found)
                                    directoryLoading = false
                                    directoryError = null
                                },
                                onError = { message ->
                                    directoryError = message
                                    directoryLoading = false
                                }
                            )
                        }
                    }
                )
                AppScreen.PROFILE -> MyListingScreen(
                    profile = if (repository.isConfigured && session == null) null else ownProfile,
                    isPublished = isPublished,
                    backendConfigured = repository.isConfigured,
                    signedInEmail = session?.email,
                    onCopy = ::copyUsername,
                    onSignIn = { screen = AppScreen.AUTH },
                    onSignOut = {
                        authLoading = true
                        runInBackground(
                            work = { repository.signOut() },
                            onResult = {
                                session = null
                                ownProfile = null
                                isPublished = false
                                authLoading = false
                            },
                            onError = { message ->
                                authLoading = false
                                authMessage = message
                            }
                        )
                    },
                    onCreate = { screen = AppScreen.ADD },
                    onEdit = { screen = AppScreen.EDIT },
                    onVisibilityChange = { visible ->
                        val active = session
                        if (repository.isConfigured && active != null) {
                            runInBackground(
                                work = {
                                    repository.setProfileVisibility(active.userId, visible)
                                    visible
                                },
                                onResult = {
                                    isPublished = it
                                    ownProfile = ownProfile?.copy(isPublished = it)
                                },
                                onError = { message -> authMessage = message }
                            )
                        } else {
                            isPublished = visible
                            ownProfile = ownProfile?.copy(isPublished = visible)
                            preferences.edit().putBoolean("published", visible).apply()
                        }
                    },
                    onDelete = {
                        val active = session
                        if (repository.isConfigured && active != null) {
                            runInBackground(
                                work = { repository.deleteOwnProfile(active.userId) },
                                onResult = {
                                    ownProfile = null
                                    isPublished = false
                                    screen = AppScreen.PROFILE
                                },
                                onError = { message -> authMessage = message }
                            )
                        } else {
                            preferences.edit().clear().apply()
                            ownProfile = null
                            isPublished = false
                            screen = AppScreen.PROFILE
                        }
                    }
                )
                AppScreen.ADD, AppScreen.EDIT -> ProfileFormScreen(
                    initialProfile = if (screen == AppScreen.EDIT) ownProfile else null,
                    onCancel = { screen = if (ownProfile == null) AppScreen.EXPLORE else AppScreen.PROFILE },
                    onSave = ::persistProfile
                )
                AppScreen.AUTH -> AuthScreen(
                    isLoading = authLoading,
                    message = authMessage,
                    onCancel = { screen = if (session == null) AppScreen.EXPLORE else AppScreen.PROFILE },
                    onAuthenticate = { email, password, createAccount ->
                        authLoading = true
                        authMessage = null
                        runInBackground(
                            work = {
                                if (createAccount) {
                                    repository.signUp(email, password)
                                } else {
                                    SupabaseAuthResult(repository.signIn(email, password), false)
                                }
                            },
                            onResult = { result ->
                                authLoading = false
                                if (result.session != null) {
                                    session = result.session
                                    screen = AppScreen.EXPLORE
                                    authMessage = null
                                } else {
                                    authMessage = "Check your email for the confirmation link. After confirming, return here and sign in."
                                }
                            },
                            onError = { message ->
                                authLoading = false
                                authMessage = message
                            }
                        )
                    }
                )
            }
        }
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("Report @${reportTarget!!.username}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (repository.isConfigured) {
                            "Choose the reason that best describes this listing."
                        } else {
                            "Reports will be submitted to the moderation service after Supabase is configured."
                        }
                    )
                    if (repository.isConfigured) {
                        listOf("spam", "harassment", "impersonation", "inappropriate", "other").forEach { reason ->
                            TextButton(
                                onClick = { selectedReportReason = reason },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    (if (selectedReportReason == reason) "● " else "○ ") +
                                        reason.replaceFirstChar { it.uppercase() }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = reportTarget!!
                    reportTarget = null
                    if (!repository.isConfigured) {
                        Toast.makeText(context, "Connect Supabase to submit reports.", Toast.LENGTH_LONG).show()
                    } else if (session == null) {
                        screen = AppScreen.AUTH
                        authMessage = "Sign in before submitting a report."
                    } else {
                        runInBackground(
                            work = { repository.reportProfile(target.id, selectedReportReason) },
                            onResult = { Toast.makeText(context, "Report submitted for review.", Toast.LENGTH_LONG).show() },
                            onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                        )
                    }
                }) { Text("Submit report") }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF9B8CFF), Color(0xFF6551D6)))),
            contentAlignment = Alignment.Center
        ) {
            Text("S", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text("snapfind", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
            Text("Find your people, worldwide", color = SnapfindColors.onSurfaceVariant, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0xFF28233D), shape = RoundedCornerShape(50.dp)) {
            Text(
                "BETA",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                color = Color(0xFFC5BAFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ExploreScreen(
    profiles: List<SnapProfile>,
    backendConfigured: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onCopy: (SnapProfile) -> Unit,
    onReport: (SnapProfile) -> Unit,
    onSearch: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("All") }
    var hasFiltered by remember { mutableStateOf(false) }
    val countries = listOf("All") + profiles.map { it.country }.filter { it.isNotBlank() }.distinct().sorted()

    LaunchedEffect(query, country, hasFiltered, backendConfigured) {
        if (backendConfigured && hasFiltered) {
            delay(350)
            onSearch(query, country)
        }
    }

    val filtered = profiles.filter { profile ->
        val matchesQuery = query.isBlank() ||
            profile.displayName.contains(query, ignoreCase = true) ||
            profile.username.contains(query.removePrefix("@"), ignoreCase = true) ||
            profile.country.contains(query, ignoreCase = true) ||
            profile.interests.contains(query, ignoreCase = true)
        val matchesCountry = country == "All" || profile.country.equals(country, ignoreCase = true)
        matchesQuery && matchesCountry
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Text("Discover people", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(
            "Find public Snapchat usernames shared by their owners.",
            color = SnapfindColors.onSurfaceVariant,
            fontSize = 14.sp
        )
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                hasFiltered = true
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search name, username or interest") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(17.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            countries.forEach { option ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .clickable {
                            country = option
                            hasFiltered = true
                        },
                    color = if (country == option) Color(0xFF8D7BEB) else Color(0xFF202332),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        option,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                        color = if (country == option) Color(0xFF14111F) else SnapfindColors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (backendConfigured) {
            Surface(color = Color(0xFF1C302A), shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF72D5A3))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE COMMUNITY DIRECTORY", color = Color(0xFF9FE5BE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Profiles", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} found", color = SnapfindColors.onSurfaceVariant, fontSize = 12.sp)
        }

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Loading community profiles…", color = SnapfindColors.onSurfaceVariant, fontSize = 13.sp)
            }
        } else if (errorMessage != null) {
            EmptyState(
                title = "Directory unavailable",
                body = errorMessage
            )
        } else if (filtered.isEmpty()) {
            EmptyState(
                title = if (backendConfigured) "No public listings yet" else "No profiles found",
                body = if (backendConfigured) "Be the first to publish a listing, or try another search." else "Try another name, interest or country."
            )
        } else {
            filtered.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    onCopy = { onCopy(profile) },
                    onReport = { onReport(profile) }
                )
            }
        }

        if (!backendConfigured) {
            Surface(
                color = Color(0xFF1C1B2A),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFBBAFFF))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Demo mode: these sample profiles are not real accounts. Configure Supabase to enable sign-in and the live directory.",
                        color = SnapfindColors.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ProfileCard(
    profile: SnapProfile,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    showReport: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191C28))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(51.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFB6A8FF), Color(0xFF5947B8)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        profile.displayName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("@${profile.username}", color = Color(0xFFB9AEFF), fontSize = 13.sp)
                }
                Surface(
                    color = if (profile.isPreview) Color(0xFF28263A) else Color(0xFF1E382F),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        if (profile.isPreview) "PREVIEW" else "YOUR LISTING",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        color = if (profile.isPreview) Color(0xFFC9C0F5) else Color(0xFF9BE0BD),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(profile.bio.ifBlank { "No bio added." }, color = SnapfindColors.onSurfaceVariant, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SmallTag(profile.country)
                profile.interests.split(",").map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach {
                    SmallTag(it)
                }
            }
            HorizontalDivider(color = Color(0xFF2A2D3B))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Copy username")
                }
                Spacer(Modifier.weight(1f))
                if (showReport) {
                    IconButton(onClick = onReport) {
                        Icon(Icons.Default.Flag, contentDescription = "Report listing", tint = SnapfindColors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallTag(label: String) {
    Surface(color = Color(0xFF272A39), shape = RoundedCornerShape(50.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color(0xFFD2CEE3),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MyListingScreen(
    profile: SnapProfile?,
    isPublished: Boolean,
    backendConfigured: Boolean,
    signedInEmail: String?,
    onCopy: (SnapProfile) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCreate: () -> Unit,
    onEdit: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("My listing", fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("Control what people can discover about you.", color = SnapfindColors.onSurfaceVariant, fontSize = 14.sp)

        if (backendConfigured && signedInEmail != null) {
            Text("Signed in as $signedInEmail", color = SnapfindColors.onSurfaceVariant, fontSize = 12.sp)
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }

        if (profile == null) {
            EmptyState(
                title = if (backendConfigured && signedInEmail == null) "Sign in to publish" else "You haven't published a listing",
                body = if (backendConfigured && signedInEmail == null) {
                    "Sign in with your email to create and manage your public Snapfind listing."
                } else {
                    "Create a voluntary public profile so people can find your Snapchat username."
                }
            )
            Button(
                onClick = if (backendConfigured && signedInEmail == null) onSignIn else onCreate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (backendConfigured && signedInEmail == null) Icons.Default.Person else Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (backendConfigured && signedInEmail == null) "Sign in / Create account" else "Create my listing")
            }
        } else {
            ProfileCard(
                profile = profile,
                onCopy = { onCopy(profile) },
                onReport = {},
                showReport = false
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF191C28)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPublished) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFFBBAFFF)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Public visibility", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isPublished) "Your listing is visible in the public directory." else "Your listing is hidden from public discovery.",
                                color = SnapfindColors.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = isPublished, onCheckedChange = onVisibilityChange)
                    }
                    HorizontalDivider(color = Color(0xFF2A2D3B))
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit listing")
                    }
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF9292))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete my listing", color = Color(0xFFFF9292))
                    }
                }
            }
        }

        Surface(color = Color(0xFF1C1B2A), shape = RoundedCornerShape(18.dp)) {
            Text(
                if (backendConfigured) {
                    "Your listing is stored in Snapfind's database. You can hide it or delete it at any time."
                } else {
                    "Prototype privacy note: your profile is stored only on this phone. It is not uploaded or searchable by other people yet."
                },
                modifier = Modifier.padding(15.dp),
                color = SnapfindColors.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your listing?") },
            text = {
                Text(
                    if (backendConfigured) {
                        "This permanently removes your public Snapfind listing. Your sign-in account remains active."
                    } else {
                        "This removes the profile saved on this device. You can create another one later."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = Color(0xFFFF9292)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
            }
        )
    }
}

@Composable
private fun AuthScreen(
    isLoading: Boolean,
    message: String?,
    onCancel: () -> Unit,
    onAuthenticate: (String, String, Boolean) -> Unit
) {
    var createAccount by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val validEmail = email.trim().contains("@") && email.trim().contains(".")
    val validPassword = password.length >= 8
    val passwordsMatch = !createAccount || password == confirmPassword
    val canSubmit = validEmail && validPassword && passwordsMatch && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(4.dp))
        Text(
            if (createAccount) "Join Snapfind" else "Welcome back",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Use an email account to manage your Snapfind listing. Snapfind never asks for your Snapchat password.",
            color = SnapfindColors.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.take(254) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email address") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it.take(128) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            supportingText = { Text("Use at least 8 characters.") }
        )
        if (createAccount) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it.take(128) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(15.dp)
            )
            if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                Text("Passwords do not match.", color = Color(0xFFFF9292), fontSize = 12.sp)
            }
        }

        if (message != null) {
            Surface(color = Color(0xFF29243D), shape = RoundedCornerShape(15.dp)) {
                Text(
                    message,
                    modifier = Modifier.padding(13.dp),
                    color = Color(0xFFD8D0FF),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Button(
            onClick = { onAuthenticate(email.trim(), password, createAccount) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    isLoading -> "Please wait…"
                    createAccount -> "Create account"
                    else -> "Sign in"
                }
            )
        }

        TextButton(
            onClick = {
                createAccount = !createAccount
                confirmPassword = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (createAccount) "Already have an account? Sign in" else "New to Snapfind? Create an account")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
            Text("Back")
        }

        Surface(color = Color(0xFF1C1B2A), shape = RoundedCornerShape(17.dp)) {
            Text(
                "Privacy: your email is used for Snapfind account access and is not shown on your public profile. Only the details you choose to publish are discoverable.",
                modifier = Modifier.padding(14.dp),
                color = SnapfindColors.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ProfileFormScreen(
    initialProfile: SnapProfile?,
    onCancel: () -> Unit,
    onSave: (SnapProfile) -> Unit
) {
    var displayName by remember(initialProfile?.id) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
    var username by remember(initialProfile?.id) { mutableStateOf(initialProfile?.username.orEmpty()) }
    var country by remember(initialProfile?.id) { mutableStateOf(initialProfile?.country.orEmpty()) }
    var interests by remember(initialProfile?.id) { mutableStateOf(initialProfile?.interests.orEmpty()) }
    var bio by remember(initialProfile?.id) { mutableStateOf(initialProfile?.bio.orEmpty()) }
    var isPublic by remember(initialProfile?.id) { mutableStateOf(initialProfile?.isPublished ?: true) }
    var consent by remember(initialProfile?.id) { mutableStateOf(initialProfile != null) }

    val cleanUsername = username.trim().removePrefix("@")
    val validUsername = cleanUsername.length in 3..15 && SnapchatUsernamePattern.matches(cleanUsername)
    val valid = displayName.isNotBlank() &&
        validUsername &&
        country.isNotBlank() &&
        consent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Spacer(Modifier.height(2.dp))
        Text(if (initialProfile == null) "Create your listing" else "Edit your listing", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Only share a Snapchat username you control and want other people to find.",
            color = SnapfindColors.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(40) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Display name") },
            placeholder = { Text("How should people know you?") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.take(15) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Snapchat username") },
            placeholder = { Text("your.username") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp),
            supportingText = { Text("3–15 characters. Start with a letter and end with a letter or number. Never enter your password.") }
        )
        OutlinedTextField(
            value = country,
            onValueChange = { country = it.take(50) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Country") },
            placeholder = { Text("e.g. Nigeria") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        OutlinedTextField(
            value = interests,
            onValueChange = { interests = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Interests") },
            placeholder = { Text("Music, football, photography") },
            singleLine = true,
            shape = RoundedCornerShape(15.dp)
        )
        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(140) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Short bio (optional)") },
            placeholder = { Text("A little about you...") },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(15.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B2A)),
            shape = RoundedCornerShape(17.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Spacer(Modifier.width(5.dp))
                Text(
                    "I own or control this Snapchat account and agree to publish this username and the profile details above for public discovery.",
                    color = SnapfindColors.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        Button(
            onClick = {
                onSave(
                    SnapProfile(
                        id = "owner",
                        displayName = displayName.trim(),
                        username = cleanUsername,
                        country = country.trim(),
                        interests = interests.trim(),
                        bio = bio.trim(),
                        isPublished = isPublic
                    )
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (initialProfile == null) "Save my listing" else "Save changes")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191C28)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A2642)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFBBAFFF))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(body, color = SnapfindColors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
