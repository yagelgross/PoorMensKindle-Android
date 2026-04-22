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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
            Color(0xFFF3F0FF), // Soft purple
            Color(0xFFF8FAFC), // Light gray/slate
            Color(0xFFFFFFFF)  // White
        )
    )

    // Handle Open Library Request Confirmation Dialog
    if (showDialog && selectedBook != null) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF1F2937),
            textContentColor = Color(0xFF4B5563),
            onDismissRequest = { showDialog = false },
            title = { Text("Request Book", fontWeight = FontWeight.Bold) },
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
                    Text("Submit", color = Color(0xFF7C3AED), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
                }
            }
        )
    }

    // --- NEW: Handle Manual Request Dialog ---
    if (showManualDialog) {
        AlertDialog(
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White,
            titleContentColor = Color(0xFF1F2937),
            textContentColor = Color(0xFF4B5563),
            onDismissRequest = { showManualDialog = false },
            title = { Text("Manual Book Request", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Can't find it in the search? Enter the details manually:")
                    OutlinedTextField(
                        value = manualTitle,
                        onValueChange = { manualTitle = it },
                        label = { Text("Book Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            focusedLabelColor = Color(0xFF7C3AED)
                        )
                    )
                    OutlinedTextField(
                        value = manualAuthor,
                        onValueChange = { manualAuthor = it },
                        label = { Text("Author") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7C3AED),
                            focusedLabelColor = Color(0xFF7C3AED)
                        )
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
                                        open_library_id = null,
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
                                    manualTitle = ""
                                    manualAuthor = ""
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = manualTitle.isNotBlank() && manualAuthor.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C3AED),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE5E7EB),
                        disabledContentColor = Color(0xFF9CA3AF)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel", color = Color(0xFF6B7280))
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
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))

        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Request a Book",
                color = Color(0xFF4C1D95),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            )

            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = Color(0xFF4B5563), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    if (NetworkManager.isAdmin) {
                        DropdownMenuItem(
                            text = { Text("Admin Panel", color = Color(0xFF1F2937)) },
                            onClick = { menuExpanded = false; onNavigateToAdmin() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Log Out", color = Color(0xFFEF4444)) },
                        onClick = {
                            menuExpanded = false
                            NetworkManager.disconnect()
                            onLogout()
                        }
                    )
                }
            }
        }

        // --- SEARCH BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by title or author...", color = Color(0xFF9CA3AF)) },
                modifier = Modifier
                    .weight(1f)
                    .shadow(2.dp, RoundedCornerShape(16.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color(0xFF1F2937),
                    unfocusedTextColor = Color(0xFF1F2937),
                    focusedBorderColor = Color(0xFF7C3AED),
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Text("Search", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        // --- NEW: Manual Entry Button ---
        TextButton(
            onClick = { showManualDialog = true },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text("Can't find it? Enter manually ✍️", color = Color(0xFF6D28D9), fontWeight = FontWeight.SemiBold)
        }

        if (isSearching) {
            CircularProgressIndicator(color = Color(0xFF7C3AED), modifier = Modifier.padding(top = 20.dp))
        } else if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = if (statusMessage.contains("success", ignoreCase = true)) Color(0xFF10B981) else Color(0xFFEF4444),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // --- RESULTS GRID ---
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
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
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF4C1D95)
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .padding(bottom = 32.dp, top = 8.dp)
                .height(48.dp)
        ) {
            Text("← Back to Library", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OpenLibraryBookCard(book: OpenLibraryBook, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = 300f),
        label = "cardScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                .aspectRatio(0.66f)
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!book.cover_url.isNullOrEmpty()) {
                AsyncImage(
                    model = book.cover_url,
                    contentDescription = "Cover for ${book.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    text = book.title,
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = book.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1F2937),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = book.author,
            fontSize = 12.sp,
            color = Color(0xFF6B7280),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        
        Text(
            text = "Published ${book.publish_year ?: "Unknown"}",
            fontSize = 11.sp,
            color = Color(0xFF9CA3AF),
            maxLines = 1
        )
    }
}