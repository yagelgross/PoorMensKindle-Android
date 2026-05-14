package com.poorMenKindle.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_books")
data class LocalBook(
    @PrimaryKey val bookId: Int,
    val title: String,
    val author: String,
    val totalChapters: Int,
    val coverBase64: String? = null,
    val summary: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null
)

@Entity(tableName = "local_highlights")
data class LocalHighlight(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val bookId: Int,
    val chapterIndex: Int,
    val highlightedText: String,
    val color: String,
    val note: String? = null,
    val scrollPercentage: Float = 0f,
    val serverHighlightId: Int? = null
)

@Entity(tableName = "downloaded_chapters")
data class LocalChapter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bookId: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val contentHtml: String
)