package com.poorMenKindle.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.poorMenKindle.android.network.BookInfo
import com.poorMenKindle.android.network.LastReadInfo
import com.poorMenKindle.android.network.NetworkManager
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun LibraryScreen(
    onNavigateToRead: (Int, Int, Int, Float) -> Unit,
    onNavigateToRequestNew: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLocalRead: (String) -> Unit,
    onNavigateToBookDetail: (Int) -> Unit,
    onLogout: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    var books by remember { mutableStateOf<List<BookInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var lastRead by remember { mutableStateOf<LastReadInfo?>(null) }
    var statusMessage by remember { mutableStateOf("Loading books from server...") }

    var showOnlyDownloaded by remember { mutableStateOf(false) }
    var downloadedBooks by remember { mutableStateOf<List<BookInfo>>(emptyList()) }
    var localDownloadedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Locks in the permission
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            onNavigateToLocalRead(uri.toString())
        }
    }

    val filteredBooks = remember(searchQuery, books, downloadedBooks, showOnlyDownloaded, localDownloadedIds) {
        var baseList = if (showOnlyDownloaded) downloadedBooks else books

        if (searchQuery.isNotBlank()) {
            baseList = baseList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.author.contains(searchQuery, ignoreCase = true) ||
                        (it.series_name?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        baseList.sortedWith(
            compareBy<BookInfo> { it.series_name?.trim()?.lowercase() ?: it.title.trim().lowercase() }
                .thenBy { it.series_number ?: Float.MAX_VALUE }
                .thenBy { it.title.trim().lowercase() }
        )
    }

    // Cooler and more welcoming gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFEFF6FF), // Soft blue
            Color(0xFFF3F4F6), // Very light gray/blue
            Color(0xFFFFFFFF)  // White
        )
    )

    // Fetch data logic
    val refreshData: () -> Unit = {
        coroutineScope.launch {
            // Load local data first and immediately
            val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
            val localBooks = withContext(Dispatchers.IO) { db.bookDao().getAllDownloadedBooks() }
            localDownloadedIds = localBooks.map { it.bookId }.toSet()
            downloadedBooks = localBooks.map {
                BookInfo(
                    id = it.bookId,
                    title = it.title,
                    author = it.author,
                    total_chapters = it.totalChapters,
                    series_name = it.seriesName,
                    series_number = it.seriesNumber,
                    date_added = "",
                    cover_image = null,
                    summary = it.summary
                )
            }

            try {
                if (!showOnlyDownloaded) {
                    statusMessage = "Refreshing..."
                }
                // Fetch books and last read in parallel-ish
                val booksResponse = withContext(Dispatchers.IO) { NetworkManager.api.getBooks() }
                val lastReadResponse = withContext(Dispatchers.IO) { NetworkManager.api.getLastRead() }

                if (booksResponse.isSuccessful) {
                    books = booksResponse.body() ?: emptyList()
                    statusMessage = if (books.isEmpty() && !showOnlyDownloaded) "No books available on the server." else ""
                } else if (booksResponse.code() == 401) {
                    NetworkManager.disconnect()
                    onLogout()
                } else {
                    statusMessage = if (!showOnlyDownloaded) "Failed to load library: ${booksResponse.code()}" else ""
                }

                if (lastReadResponse.isSuccessful) {
                    lastRead = lastReadResponse.body()
                }
            } catch (e: Exception) {
                if (!showOnlyDownloaded) {
                    statusMessage = "Connection error: ${e.message}"
                } else {
                    statusMessage = ""
                }
            }
        }
    }

    // Fetch data on screen load
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            refreshData()
        }
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
                text = "Poor Men's Kindle \uD83D\uDE0C",
                color = Color(0xFF1E3A8A), // Deep blue
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.5).sp
            )

            var menuExpanded by remember { mutableStateOf(false) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = refreshData) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Button"
                    )
                }

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
                            text = { Text("Request Book", color = Color(0xFF1F2937)) },
                            onClick = { menuExpanded = false; onNavigateToRequestNew() }
                        )
                        DropdownMenuItem(
                            text = { Text("Offline EPUB", color = Color(0xFF1F2937)) },
                            onClick = { menuExpanded = false; filePickerLauncher.launch(arrayOf("application/epub+zip")) }
                        )
                        DropdownMenuItem(
                            text = { Text("Log Out", color = Color(0xFFEF4444)) },
                            onClick = {
                                menuExpanded = false
                                NetworkManager.disconnect()
                                onLogout()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Licenses & About", color = Color(0xFF1F2937)) },
                            onClick = {
                                menuExpanded = false
                                // Create intent to launch the Google OSS Activity
                                onNavigateToLicenses()
                            }
                        )
                    }
                }
            }
        }

        // --- CONTINUE READING WIDGET ---
        lastRead?.let { readInfo ->
            val isHebrew = remember(readInfo.title) {
                readInfo.title.any { it in '\u0590'..'\u05FF' }
            }
            val layoutDirection = if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr

            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))
                            )
                        )
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHebrew) "המשך מהמקום האחרון" else "Continue Reading",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = readInfo.title,
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isHebrew) "פרק ${readInfo.chapter_index + 1}" else "Chapter ${readInfo.chapter_index + 1}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    onNavigateToRead(
                                        readInfo.book_id,
                                        readInfo.total_chapters,
                                        readInfo.chapter_index,
                                        readInfo.scroll_progress
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF2563EB)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                            ) {
                                Text(if (isHebrew) "המשך" else "Resume", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // --- SEARCH BAR ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title or author...", color = Color(0xFF9CA3AF)) },
            leadingIcon = { Text("🔍", fontSize = 16.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp)
                .shadow(2.dp, RoundedCornerShape(20.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color(0xFF1F2937),
                unfocusedTextColor = Color(0xFF1F2937)
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true
        )

        // --- CLOUD / OFFLINE FILTER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showOnlyDownloaded,
                onClick = { showOnlyDownloaded = false },
                label = { Text("☁️ All Books") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFF1E3A8A)
                )
            )
            FilterChip(
                selected = showOnlyDownloaded,
                onClick = { showOnlyDownloaded = true },
                label = { Text("📱 Downloaded") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
                    selectedLabelColor = Color(0xFF1E3A8A)
                )
            )
        }

        // --- LIBRARY GALLERY ---
        if (statusMessage.isNotEmpty() && !showOnlyDownloaded) {
            Text(
                text = statusMessage,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        if (showOnlyDownloaded && downloadedBooks.isEmpty() && statusMessage.isEmpty()) {
             Text(
                text = "No downloaded books yet.",
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(filteredBooks) { book ->
                BookCard(book = book, onClick = {
                    onNavigateToBookDetail(book.id)
                })
            }
        }
    }
}


@Composable
fun BookCard(book: BookInfo, onClick: () -> Unit) {
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
                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFF3B82F6).copy(alpha = 0.1f))
                .background(Color(0xFFE5E7EB), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // --- COIL REPLACES THE MANUAL FETCHING & CACHING ---
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("${NetworkManager.BASE_URL}/books/${book.id}/cover")
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover for ${book.title}",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
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

        if (!book.series_name.isNullOrEmpty()) {
            val numStr = book.series_number?.let {
                if (it % 1.0 == 0.0) "#${it.toInt()}" else "#$it"
            } ?: ""

            Text(
                text = "${book.series_name.trim()} $numStr",
                fontSize = 12.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Text(
            text = book.author,
            fontSize = 12.sp,
            color = Color(0xFF9CA3AF),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}


