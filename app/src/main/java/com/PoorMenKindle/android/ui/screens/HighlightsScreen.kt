package com.PoorMenKindle.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun HighlightsScreen(
    bookId: Int,
    returnChapter: Int,
    returnScroll: Float,
    onNavigateBack: () -> Unit,
    onNavigateToRead: (Int, Int, Int, Float, Int, Float) -> Unit
) {
    val context = LocalContext.current
    var highlights by remember { mutableStateOf<List<HighlightItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalChapters by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    var viewNoteHighlight by remember { mutableStateOf<HighlightItem?>(null) }
    var editNoteHighlight by remember { mutableStateOf<HighlightItem?>(null) }

    LaunchedEffect(bookId) {
        val db = AppDatabase.getDatabase(context)
        val dao = db.bookDao()

        // 1. Initial Local Load
        val localBook = withContext(Dispatchers.IO) { dao.getDownloadedBook(bookId) }
        if (localBook != null) totalChapters = localBook.totalChapters
        else {
            try {
                val res = withContext(Dispatchers.IO) { NetworkManager.api.getBook(bookId) }
                if (res.isSuccessful) totalChapters = res.body()?.total_chapters ?: 0
            } catch (e: Exception) {}
        }

        val localHls = withContext(Dispatchers.IO) { dao.getHighlightsForBook(bookId) }
        withContext(Dispatchers.Main) {
            highlights = localHls.map {
                HighlightItem(it.localId, it.chapterIndex, it.highlightedText, it.note, it.color, it.scrollPercentage, "")
            }
            isLoading = false
        }

        // 2. Sync from Server
        try {
            val response = withContext(Dispatchers.IO) { NetworkManager.api.getHighlights(bookId) }
            if (response.isSuccessful) {
                val serverHls = response.body() ?: emptyList()
                withContext(Dispatchers.IO) {
                    serverHls.forEach { serverHl ->
                        val existsLocally = localHls.any { it.highlightedText == serverHl.highlighted_text && it.chapterIndex == serverHl.chapter_index }
                        if (!existsLocally) {
                            dao.insertHighlight(
                                LocalHighlight(
                                    bookId = bookId,
                                    chapterIndex = serverHl.chapter_index,
                                    highlightedText = serverHl.highlighted_text,
                                    color = serverHl.color ?: "rgba(255, 235, 59, 0.4)",
                                    note = serverHl.note,
                                    scrollPercentage = serverHl.scroll_percentage
                                )
                            )
                        }
                    }
                }
                val updatedLocalHls = withContext(Dispatchers.IO) { dao.getHighlightsForBook(bookId) }
                withContext(Dispatchers.Main) {
                    highlights = updatedLocalHls.map {
                        HighlightItem(it.localId, it.chapterIndex, it.highlightedText, it.note, it.color, it.scrollPercentage, "")
                    }
                }
            }
        } catch (e: Exception) {}
    }

    // View Note Dialog
    if (viewNoteHighlight != null) {
        AlertDialog(
            onDismissRequest = { viewNoteHighlight = null },
            title = { Text("Note", color = Color(0xFF4dd0e1)) },
            text = { Text(viewNoteHighlight!!.note ?: "", color = Color.White) },
            confirmButton = { TextButton(onClick = { viewNoteHighlight = null }) { Text("Close") } },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    // Edit/Add Note Dialog
    if (editNoteHighlight != null) {
        var noteText by remember { mutableStateOf(editNoteHighlight!!.note ?: "") }
        AlertDialog(
            onDismissRequest = { editNoteHighlight = null },
            title = { Text(if (editNoteHighlight!!.note.isNullOrBlank()) "Add Note" else "Edit Note", color = Color(0xFF4dd0e1)) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color(0xFF4dd0e1),
                        focusedIndicatorColor = Color(0xFF4dd0e1),
                        unfocusedIndicatorColor = Color.Gray
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val hl = editNoteHighlight!!
                    editNoteHighlight = null
                    coroutineScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(context)
                        db.bookDao().updateHighlightNote(hl.id, noteText)
                        val updated = db.bookDao().getHighlightsForBook(bookId)
                        withContext(Dispatchers.Main) {
                            highlights = updated.map {
                                HighlightItem(it.localId, it.chapterIndex, it.highlightedText,
                                    it.note, it.color, it.scrollPercentage, "")
                            }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editNoteHighlight = null }) { Text("Cancel") } },
            containerColor = Color(0xFF2b2b2b)
        )
    }

    Scaffold(
        containerColor = Color(0xFF1E1E1E),
        topBar = {
            TopAppBar(
                title = { Text("My Highlights") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            items(highlights) { highlight ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).clickable {
                        if (!highlight.note.isNullOrBlank()) viewNoteHighlight = highlight
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C),
                        contentColor = Color.White
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Chapter ${highlight.chapter_index + 1}", color = Color.Gray)
                        Text("\"${highlight.highlighted_text}\"")
                        if (!highlight.note.isNullOrBlank()) {
                            Text("📝 ${highlight.note}", color = Color.LightGray, fontStyle = FontStyle.Italic)
                        }

                        // Action Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row {
                                TextButton(onClick = { editNoteHighlight = highlight }) { Text(if (highlight.note.isNullOrBlank()) "Add Note" else "Edit Note") }
                                TextButton(onClick = { onNavigateToRead(bookId, totalChapters, highlight.chapter_index, highlight.scroll_percentage, returnChapter, returnScroll) }) { Text("Go To ➔") }
                            }
                            IconButton(onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val db = AppDatabase.getDatabase(context)
                                    db.bookDao().deleteHighlight(highlight.id)
                                    val updated = db.bookDao().getHighlightsForBook(bookId)
                                    withContext(Dispatchers.Main) {
                                        highlights = updated.map {
                                            HighlightItem(it.localId, it.chapterIndex, it.highlightedText, it.note, it.color, it.scrollPercentage, "")
                                        }
                                    }
                                }
                            }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}