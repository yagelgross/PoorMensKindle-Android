package com.poorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.poorMenKindle.android.network.BookInfo
import com.poorMenKindle.android.network.NetworkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.poorMenKindle.android.ui.theme.appBackgroundBrush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

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
    var localCoverBase64 by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val context = androidx.compose.ui.platform.LocalContext.current
    var isDownloadedLocally by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val backgroundBrush = appBackgroundBrush()

    LaunchedEffect(bookId) {
        coroutineScope.launch {
            try {
                isLoading = true
                isError = false

                val db = com.poorMenKindle.android.data.local.AppDatabase.getDatabase(context)
                val localBook = withContext(Dispatchers.IO) { db.bookDao().getDownloadedBook(bookId) }
                isDownloadedLocally = (localBook != null)
                localCoverBase64 = localBook?.coverBase64

                if (isDownloadedLocally && localBook != null) {
                    book = BookInfo(
                        id = localBook.bookId,
                        title = localBook.title,
                        author = localBook.author,
                        total_chapters = localBook.totalChapters,
                        series_name = localBook.seriesName,
                        series_number = localBook.seriesNumber,
                        date_added = "",
                        cover_image = null,
                        summary = localBook.summary
                    )
                    isLoading = false // Loaded locally, show immediately
                }

                try {
                    val bookResponse = withContext(Dispatchers.IO) { NetworkManager.api.getBook(bookId) }
                    if (bookResponse.isSuccessful) {
                        book = bookResponse.body()
                    }

                    val progResponse = withContext(Dispatchers.IO) { NetworkManager.api.getProgress(bookId) }
                    if (progResponse.isSuccessful) {
                        currentChapter = progResponse.body()?.current_chapter ?: 0
                        currentScroll = progResponse.body()?.scroll_progress ?: 0f
                    }
                } catch (e: Exception) {
                    if (!isDownloadedLocally) {
                        isError = true
                        errorMessage = when (e) {
                            is java.net.UnknownHostException, is java.net.ConnectException -> "No Internet Connection"
                            else -> "An error occurred: ${e.message}"
                        }
                    }
                    // Ignore network error if already downloaded
                }
            } finally {
                if (!isDownloadedLocally || isError) {
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (isError) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        coroutineScope.launch {
                            // Re-trigger load
                            onNavigateBack() // Or implement a retry logic
                        }
                    }) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            book?.let { b ->
                val isHebrew = isTextHebrew(b.title) || isTextHebrew(b.summary)
                val layoutDir = if (isHebrew) LayoutDirection.Rtl else LayoutDirection.Ltr

                // Set layout direction based on content language
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageSource: Any = if (isDownloadedLocally && localCoverBase64 != null) {
                                    android.util.Base64.decode(localCoverBase64, android.util.Base64.DEFAULT)
                                } else {
                                    "${NetworkManager.BASE_URL}/books/${b.id}/cover"
                                }

                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imageSource)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Cover",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
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
                                color = MaterialTheme.colorScheme.onBackground,
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
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }


                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "by ${b.author}",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = FontStyle.Italic
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (isHebrew) "פרקים: ${b.total_chapters}" else "Total Chapters: ${b.total_chapters}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = if (currentChapter > 0) "Continue Reading (Ch. ${currentChapter + 1})" else "Start Reading",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (isDownloading) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Downloading... ${(downloadProgress * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        } else if (isDownloadedLocally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅ Saved for Offline Reading", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        com.poorMenKindle.android.data.local.OfflineSyncManager.removeLocalBook(context, b.id)
                                        isDownloadedLocally = false
                                    }
                                },
                                modifier = Modifier.background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Download", tint = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isDownloading = true
                                    downloadError = ""
                                    coroutineScope.launch {
                                        val success = com.poorMenKindle.android.data.local.OfflineSyncManager.downloadBookFull(
                                            context = context,
                                            bookId = b.id,
                                            title = b.title,
                                            author = b.author,
                                            totalChapters = b.total_chapters
                                        ) { current, total ->
                                            downloadProgress = current.toFloat() / total.toFloat()
                                        }
                                        isDownloading = false
                                        if (success) {
                                            isDownloadedLocally = true
                                        } else {
                                            downloadError = "Download failed — check your connection and try again."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text("⬇ Download for Offline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            if (downloadError.isNotEmpty()) {
                                Text(
                                    text = downloadError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Summary Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Takes up the remaining screen space
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isHebrew) "תקציר" else "Synopsis",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = b.summary ?: if (isHebrew) "אין תקציר זמין לספר זה." else "No summary available for this book.",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp,
                                    // Text alignment based on language
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