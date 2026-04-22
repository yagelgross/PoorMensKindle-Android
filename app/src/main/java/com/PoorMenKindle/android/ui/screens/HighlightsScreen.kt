package com.PoorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.PoorMenKindle.android.data.local.AppDatabase
import com.PoorMenKindle.android.data.local.LocalHighlight
import com.PoorMenKindle.android.network.HighlightItem
import com.PoorMenKindle.android.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(bookId: Int, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var highlights by remember { mutableStateOf<List<HighlightItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // שליפת הנתונים: קודם מהמכשיר, אחר כך סנכרון מהשרת (Offline-First)
    LaunchedEffect(bookId) {
        coroutineScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val dao = db.bookDao()

            // 1. טעינה מקומית מיידית
            var localHls = dao.getHighlightsForBook(bookId)
            highlights = localHls.map {
                HighlightItem(it.localId, it.chapterIndex, it.highlightedText, null, it.color, "")
            }
            isLoading = false

            // 2. סנכרון מול השרת
            try {
                val response = NetworkManager.api.getHighlights(bookId)
                if (response.isSuccessful) {
                    val serverHls = response.body() ?: emptyList()

                    // שמירת הדגשות חדשות מהשרת למכשיר
                    serverHls.forEach { serverHl ->
                        val existsLocally = localHls.any {
                            it.highlightedText == serverHl.highlighted_text && it.chapterIndex == serverHl.chapter_index
                        }
                        if (!existsLocally) {
                            dao.insertHighlight(
                                LocalHighlight(
                                    bookId = bookId,
                                    chapterIndex = serverHl.chapter_index,
                                    highlightedText = serverHl.highlighted_text,
                                    color = serverHl.color ?: "rgba(255, 235, 59, 0.4)"
                                )
                            )
                        }
                    }

                    // רענון הרשימה המקומית אחרי הסנכרון
                    localHls = dao.getHighlightsForBook(bookId)
                    highlights = localHls.map {
                        HighlightItem(it.localId, it.chapterIndex, it.highlightedText, null, it.color, "")
                    }
                }
            } catch (e: Exception) {
                // מתעלמים - אם אין אינטרנט, הנתונים המקומיים מספיקים
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Highlights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E), titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4dd0e1))
            }
        } else if (highlights.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No highlights yet.", color = Color.Gray, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(highlights, key = { it.id }) { highlight ->
                    val composeColor = when (highlight.color) {
                        "rgba(255, 235, 59, 0.4)" -> Color(0xFFFFF59D) // Yellow
                        "rgba(76, 175, 80, 0.4)" -> Color(0xFFA5D6A7)  // Green
                        "rgba(33, 150, 243, 0.4)" -> Color(0xFF90CAF9) // Blue
                        "rgba(244, 67, 54, 0.4)" -> Color(0xFFEF9A9A)  // Red
                        "rgba(156, 39, 176, 0.4)" -> Color(0xFFCE93D8) // purple
                        else -> Color(0xFFFFF59D)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2b2b2b)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(composeColor))

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Chapter ${highlight.chapter_index + 1}",
                                    color = composeColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "\"${highlight.highlighted_text}\"",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 22.sp
                                )
                            }

                            IconButton(onClick = {
                                highlights = highlights.filter { it.id != highlight.id }

                                coroutineScope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.bookDao().deleteHighlight(highlight.id)
                                    try {
                                        val serverRes = NetworkManager.api.getHighlights(bookId)
                                        if (serverRes.isSuccessful) {
                                            val serverHls = serverRes.body() ?: emptyList()
                                            val targetHl = serverHls.find {
                                                it.highlighted_text == highlight.highlighted_text &&
                                                        it.chapter_index == highlight.chapter_index
                                            }
                                            if (targetHl != null) {
                                                NetworkManager.api.deleteHighlight(targetHl.id)
                                            }
                                        }
                                    } catch (e: Exception) {
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}