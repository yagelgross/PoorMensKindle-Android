package com.PoorMenKindle.android.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
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
import com.PoorMenKindle.android.network.BookInfo
import com.PoorMenKindle.android.network.LastReadInfo
import com.PoorMenKindle.android.network.NetworkManager
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle

@Composable
fun LibraryScreen(
    onNavigateToRead: (Int, Int, Int) -> Unit,
    onNavigateToRequestNew: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToLocalRead: (String) -> Unit,
    onNavigateToBookDetail: (Int) -> Unit,
    onLogout: () -> Unit
) {
    var books by remember { mutableStateOf<List<BookInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var lastRead by remember { mutableStateOf<LastReadInfo?>(null) }
    var statusMessage by remember { mutableStateOf("Loading books from server...") }
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

    val filteredBooks = remember(searchQuery, books) {
        val baseList = if (searchQuery.isBlank()) {
            books
        } else {
            books.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.author.contains(searchQuery, ignoreCase = true) ||
                        (it.series_name?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

        // --- THE MAGIC SORT ---
        baseList.sortedWith(
            compareBy<BookInfo> { it.series_name?.trim()?.lowercase() ?: it.title.trim().lowercase() }
                // 2. Sort by number
                .thenBy { it.series_number ?: Float.MAX_VALUE }
                // 3. Fallback to title
                .thenBy { it.title.trim().lowercase() }
        )
    }

    // Premium Gradient Background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFBFF5AC),
            Color(0xFFB7F397),
            Color(0xFF95D473),
            Color(0xFF76D055),
            Color(0xFF1C1B1B)
        )
    )

    // Fetch data on screen load
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED) {
            coroutineScope.launch {
                try {
                    // Fetch books and last read in parallel-ish
                    val booksResponse = withContext(Dispatchers.IO) { NetworkManager.api.getBooks() }
                    val lastReadResponse = withContext(Dispatchers.IO) { NetworkManager.api.getLastRead() }

                    if (booksResponse.isSuccessful) {
                        books = booksResponse.body() ?: emptyList()
                        statusMessage = if (books.isEmpty()) "No books available on the server." else ""
                    } else if (booksResponse.code() == 401) {
                        NetworkManager.disconnect()
                        onLogout()
                        return@launch
                    } else {
                        statusMessage = "Failed to load library: ${booksResponse.code()}"
                    }

                    if (lastReadResponse.isSuccessful) {
                        lastRead = lastReadResponse.body()
                    }
                } catch (e: Exception) {
                    statusMessage = "Connection error: ${e.message}"
                }
            }
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
                .background(Color.White.copy(alpha = 0.1f))
                .border(1.dp, Color.White.copy(alpha = 0.2f))
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📚 BookWormHole",
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )

            // Mobile Dropdown Menu (Fixes buttons being pushed off the screen)
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(Color.Cyan)
                ) {
                    if (NetworkManager.isAdmin) {
                        DropdownMenuItem(
                            text = { Text("⚙ Admin Panel", color = Color.Black, fontWeight = FontWeight.Bold) },
                            onClick = { menuExpanded = false; onNavigateToAdmin() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("➕ Request Book", color = Color.Black, fontWeight = FontWeight.Bold) },
                        onClick = { menuExpanded = false; onNavigateToRequestNew() }
                    )
                    DropdownMenuItem(
                        text = { Text("📂 Offline EPUB", color = Color.Black, fontWeight = FontWeight.Bold) },
                        onClick = { menuExpanded = false; filePickerLauncher.launch(arrayOf("application/epub+zip")) }
                    )
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

        // --- CONTINUE READING WIDGET ---
        lastRead?.let { readInfo ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "\"${readInfo.title}\" — Chapter ${readInfo.chapter_index + 1}",
                        fontSize = 12.sp,
                        color = Color(0xFF032579),
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Button(
                        onClick = { onNavigateToRead(readInfo.book_id, readInfo.total_chapters, readInfo.chapter_index) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4dd0e1)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("▶ Resume Reading", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- SEARCH BAR ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title or author...", color = Color.DarkGray) },
            leadingIcon = { Text("🔍", fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp)
                .padding(bottom = 15.dp), // Space between search bar and Library text
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.5f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.3f),
                focusedBorderColor = Color(0xFF032579),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true
        )

        // --- LIBRARY GALLERY ---
        Text(
            text = "Your Library",
            fontSize = 24.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                color = Color.Black.copy(alpha = 0.7f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // This forces the grid to fill all available empty space
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
    var coverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    // Track if the user is currently pressing the card
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring animations for scale and shadow (matches your 1.05x JavaFX scale and 30 radius shadow)
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

    // Fetch or Load the cover image
    LaunchedEffect(book.id) {
        if (CoverCache.bitmaps.containsKey(book.id)) {
            coverBitmap = CoverCache.bitmaps[book.id]
        } else {
            try {
                val response = withContext(Dispatchers.IO) { NetworkManager.api.getCover(book.id) }
                if (response.isSuccessful) {
                    response.body()?.cover_image?.let { base64String ->
                        val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).asImageBitmap()
                        CoverCache.bitmaps[book.id] = bitmap
                        coverBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                // Leave bitmap null
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Removes the default gray ripple to keep your clean aesthetic
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
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap!!,
                    contentDescription = "Cover for ${book.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = book.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = book.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF204CCE),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        if (!book.series_name.isNullOrEmpty()) {
            val numStr = book.series_number?.let {
                // Format 1.0 as "1", but keep 1.5 as "1.5"
                if (it % 1.0 == 0.0) "#${it.toInt()}" else "#$it"
            } ?: ""

            Text(
                text = book.series_name.trim(),
                fontSize = 10.sp,
                color = Color(0xFF204CCE), // Your cyan theme color
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = numStr,
                fontSize = 10.sp,
                color = Color(0xFF204CCE),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

        }

        Text(
            text = book.author,
            fontSize = 11.sp,
            color = Color(0xFF032579), // Changed to a deep navy blue to match your "Continue Reading" text
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// --- MEMORY CACHE ---
object CoverCache {
    val bitmaps = mutableMapOf<Int, androidx.compose.ui.graphics.ImageBitmap>()
}