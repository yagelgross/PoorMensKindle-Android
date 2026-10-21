package com.poorMenKindle.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.poorMenKindle.android.network.HighlightItem
import com.poorMenKindle.android.ui.viewmodel.HighlightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HighlightsScreen(
    bookId: Int,
    returnChapter: Int,
    returnScroll: Float,
    onNavigateBack: () -> Unit,
    onNavigateToRead: (Int, Int, Int, Float, Int, Float) -> Unit
) {
    val viewModel: HighlightsViewModel = viewModel(
        key = "highlights_$bookId",
        factory = HighlightsViewModel.factory(bookId)
    )
    val highlights = viewModel.highlights
    val totalChapters = viewModel.totalChapters

    var viewNoteHighlight by remember { mutableStateOf<HighlightItem?>(null) }
    var editNoteHighlight by remember { mutableStateOf<HighlightItem?>(null) }

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
                    viewModel.updateNote(hl, noteText)
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

                        // Action buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row {
                                TextButton(onClick = { editNoteHighlight = highlight }) { Text(if (highlight.note.isNullOrBlank()) "Add Note" else "Edit Note") }
                                TextButton(onClick = { onNavigateToRead(bookId, totalChapters, highlight.chapter_index, highlight.scroll_percentage, returnChapter, returnScroll) }) { Text("Go To ➔") }
                            }
                            IconButton(onClick = { viewModel.deleteHighlight(highlight) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}
