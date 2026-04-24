package com.PoorMenKindle.android.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.PoorMenKindle.android.network.BookInfo
import com.PoorMenKindle.android.network.NetworkManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToRead: (Int, Int, Int, Float) -> Unit
) {
    var book by remember { mutableStateOf<BookInfo?>(null) }
    var currentChapter by remember { mutableStateOf(0) }
    var currentScroll by remember { mutableFloatStateOf(0f) }
    var coverBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    var isDownloadedLocally by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFBFF5AC),
            Color(0xFFB7F397),
            Color(0xFF95D473),
            Color(0xFF76D055),
            Color(0xFF1C1B1B)
        )
    )

    LaunchedEffect(bookId) {
        coroutineScope.launch {
            try {

                val db = com.PoorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                val localBook = withContext(Dispatchers.IO) { db.bookDao().getDownloadedBook(bookId) }
                isDownloadedLocally = (localBook != null)

                // 1. Fetch the specific book details
                val bookResponse = withContext(Dispatchers.IO) { NetworkManager.api.getBook(bookId) }
                if (bookResponse.isSuccessful) {
                    book = bookResponse.body()
                }

                // 2. Fetch the user's reading progress for this book
                val progResponse = withContext(Dispatchers.IO) { NetworkManager.api.getProgress(bookId) }
                if (progResponse.isSuccessful) {
                    currentChapter = progResponse.body()?.current_chapter ?: 0
                    currentScroll = progResponse.body()?.scroll_progress ?: 0f
                }

                // 3. Load the cover image (Check memory cache first to save network calls!)
                val cachedCover = CoverCache.bitmaps.get(bookId)

                if (cachedCover != null) {
                    coverBitmap = cachedCover
                } else {
                    val coverResponse = withContext(Dispatchers.IO) { NetworkManager.api.getCover(bookId) }
                    coverResponse.body()?.cover_image?.let { base64String ->
                        val bytes = Base64.decode(base64String, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()

                        CoverCache.bitmaps.put(bookId, bitmap)
                        coverBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                // Handle network error
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.background(backgroundBrush)
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF032579))
            }
        } else {
            book?.let { b ->
                val isHebrew = isTextHebrew(b.title) || isTextHebrew(b.summary)
                val layoutDir = if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr

                // 2. Force RTL or LTR specifically for this screen's content
                CompositionLocalProvider(LocalLayoutDirection provides layoutDir) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(160.dp) // Reduced from 220.dp
                                    .width(110.dp)  // Reduced from 150.dp
                                    .shadow(12.dp, RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (coverBitmap != null) {
                                    Image(
                                        bitmap = coverBitmap!!,
                                        contentDescription = "Cover",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text("No Cover", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                text = b.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                lineHeight = 28.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                if (!b.series_name.isNullOrEmpty()) {
                                    val numStr = b.series_number?.let {
                                        if (it % 1.0 == 0.0) "#${it.toInt()}" else "#$it"
                                    } ?: ""
                                    Text(
                                        text = "${b.series_name} $numStr".trim(),
                                        fontSize = 14.sp,
                                        color = Color(0xFFe67e22),
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }


                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "by ${b.author}",
                                    fontSize = 16.sp,
                                    color = Color(0xFF032579),
                                    fontStyle = FontStyle.Italic
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isHebrew) "פרקים: ${b.total_chapters}" else "Total Chapters: ${b.total_chapters}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF032579)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        // Read Button
                        Button(
                            onClick = { onNavigateToRead(b.id, b.total_chapters, currentChapter, currentScroll) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(55.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4dd0e1)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = if (currentChapter > 0) "Continue Reading (Ch. ${currentChapter + 1})" else "Start Reading",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isDownloading) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Downloading... ${(downloadProgress * 100).toInt()}%", color = Color(0xFF032579), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF4dd0e1),
                                    trackColor = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        } else if (isDownloadedLocally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅ Saved for Offline Reading", color = Color(0xFF2ecc71), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.PoorMenKindle.android.data.local.OfflineSyncManager.removeLocalBook(context, b.id)
                                        isDownloadedLocally = false
                                    }
                                },
                                modifier = Modifier.background(Color(0xFFe74c3c).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = Color(0xFFe74c3c))
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isDownloading = true
                                    coroutineScope.launch {
                                        val success = com.PoorMenKindle.android.data.local.OfflineSyncManager.downloadBookFull(
                                            context = context,
                                            bookId = b.id,
                                            title = b.title,
                                            author = b.author,
                                            totalChapters = b.total_chapters
                                        ) { current, total ->
                                            downloadProgress = current.toFloat() / total.toFloat()
                                        }
                                        isDownloading = false
                                        if (success) isDownloadedLocally = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF032579)),
                                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF032579))
                            ) {
                                Text("⬇ Download for Offline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- SUMMARY BOX ---
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Takes up the remaining screen space
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isHebrew) "תקציר" else "Synopsis",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = b.summary ?: if (isHebrew) "אין תקציר זמין לספר זה." else "No summary available for this book.",
                                    fontSize = 15.sp,
                                    color = Color.DarkGray,
                                    lineHeight = 22.sp,
                                    // Use Justify for English to look like a book, but Start for Hebrew for clean RTL edges
                                    textAlign = if (isHebrew) TextAlign.Start else TextAlign.Justify
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun isTextHebrew(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    val hebrewRegex = Regex("[\\u0590-\\u05FF]")
    return hebrewRegex.containsMatchIn(text)
}