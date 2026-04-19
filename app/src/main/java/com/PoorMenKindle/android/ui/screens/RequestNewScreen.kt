package com.PoorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import com.PoorMenKindle.android.network.BookRequestCreate
import com.PoorMenKindle.android.network.NetworkManager
import com.PoorMenKindle.android.network.OpenLibraryBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestNewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<OpenLibraryBook>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    var selectedBook by remember { mutableStateOf<OpenLibraryBook?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // --- Manual Request States ---
    var showManualDialog by remember { mutableStateOf(false) }
    var manualTitle by remember { mutableStateOf("") }
    var manualAuthor by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFaa92e8),
            Color(0xFF9e5aef),
            Color(0xFF7d3fe8),
            Color(0xFF5e00ff),
            Color(0xFF0d0d0d)
        )
    )

    // Handle Open Library Request Confirmation Dialog
    if (showDialog && selectedBook != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Request Book") },
            text = { Text("Would you like to submit a request to the admin to add '${selectedBook!!.title}' to the library?") },
            confirmButton = {
                TextButton(onClick = {
                    val bookToRequest = selectedBook!!
                    showDialog = false
                    coroutineScope.launch {
                        try {
                            val requestPayload = BookRequestCreate(
                                open_library_id = bookToRequest.book_id,
                                title = bookToRequest.title,
                                author = bookToRequest.author,
                                cover_url = bookToRequest.cover_url
                            )
                            val response = withContext(Dispatchers.IO) {
                                NetworkManager.api.submitBookRequest(requestPayload)
                            }
                            statusMessage = if (response.isSuccessful) {
                                "Book request submitted successfully!"
                            } else {
                                "Failed to submit request. You may have already requested this book."
                            }
                        } catch (e: Exception) {
                            statusMessage = "Error: ${e.message}"
                        }
                    }
                }) {
                    Text("Submit", color = Color(0xFF5e00ff))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    // --- NEW: Handle Manual Request Dialog ---
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Manual Book Request") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Can't find it in the search? Enter the details manually:")
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Book Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualAuthor,
                        onValueChange = { manualAuthor = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualTitle.isNotBlank() && manualAuthor.isNotBlank()) {
                            showManualDialog = false
                            coroutineScope.launch {
                                try {
                                    val requestPayload = BookRequestCreate(
                                        title = manualTitle.trim(),
                                        author = manualAuthor.trim(),
                                        open_library_id = null, // Explicitly null for manual
                                        cover_url = null
                                    )
                                    val response = withContext(Dispatchers.IO) {
                                        NetworkManager.api.submitBookRequest(requestPayload)
                                    }
                                    statusMessage = if (response.isSuccessful) {
                                        "Manual request submitted successfully!"
                                    } else {
                                        "Failed to submit. You may have already requested this."
                                    }
                                    // Clear fields after success
                                    manualTitle = ""
                                    manualAuthor = ""
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                }
                            }
                        }
                    },
                    // Disable the button if fields are empty
                    enabled = manualTitle.isNotBlank() && manualAuthor.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5e00ff), // Background color when enabled
                        contentColor = Color.White,         // Text color when enabled
                        disabledContainerColor = Color.DarkGray, // Background when disabled
                        disabledContentColor = Color.LightGray   // Text when disabled
                    )
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📚 Request SubDivision",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // Mobile Dropdown Menu
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    if (NetworkManager.isAdmin) {
                        DropdownMenuItem(
                            text = { Text("⚙ Admin Panel", color = Color.Black, fontWeight = FontWeight.Bold) },
                            onClick = { menuExpanded = false; onNavigateToAdmin() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("🚪 Log Out", color = Color.Red, fontWeight = FontWeight.Bold) },
                        onClick = {
                            menuExpanded = false
                            NetworkManager.disconnect()
                            onLogout()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- SEARCH BAR ---
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title or author...", color = Color(0xFFc0c0c0)) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4dd0e1),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(20.dp),
                singleLine = true
            )

            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        isSearching = true
                        statusMessage = ""
                        coroutineScope.launch {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    NetworkManager.api.searchOpenLibrary(searchQuery)
                                }
                                if (response.isSuccessful) {
                                    searchResults = response.body() ?: emptyList()
                                    if (searchResults.isEmpty()) statusMessage = "No books found."
                                } else {
                                    statusMessage = "Search failed: ${response.code()}"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Connection error: ${e.message}"
                            } finally {
                                isSearching = false
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4dd0e1)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Text("Search", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // --- NEW: Manual Entry Button ---
        Button(
            onClick = { showManualDialog = true },
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4dd0e1)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("Can't find it? ✍️", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isSearching) {
            CircularProgressIndicator(color = Color(0xFF4dd0e1))
        } else if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = Color(0xFF00e5ff), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }

        // --- RESULTS GRID ---
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(searchResults) { book ->
                OpenLibraryBookCard(book = book) {
                    selectedBook = book
                    showDialog = true
                }
            }
        }

        // --- BOTTOM BAR ---
        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4dd0e1)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text("Home", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OpenLibraryBookCard(book: OpenLibraryBook, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "cardScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isPressed) 25.dp else 12.dp,
        animationSpec = spring(stiffness = 300f),
        label = "cardShadow"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(100.dp, 130.dp)
                .shadow(shadowElevation, RoundedCornerShape(8.dp))
                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!book.cover_url.isNullOrEmpty()) {
                AsyncImage(
                    model = book.cover_url,
                    contentDescription = "Cover for ${book.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = book.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(text = book.author, fontSize = 11.sp, color = Color(0xFFda1f1f), maxLines = 1)
        Text(text = "Published ${book.publish_year ?: "Unknown"}", fontSize = 11.sp, color = Color.LightGray)
    }
}