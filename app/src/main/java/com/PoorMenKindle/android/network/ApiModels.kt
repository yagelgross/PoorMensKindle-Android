package com.PoorMenKindle.android.network

import androidx.annotation.Keep

// --- Auth & User ---
@Keep
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val is_admin: Boolean
)

@Keep
data class UserInfo(
    val id: Int,
    val username: String,
    val isAdmin: Boolean,
    val createdAt: String,
    val lastLogin: String
)

@Keep
data class AdminAddBookRequest(
    val title: String,
    val author: String,
    val total_chapters: Int,
    val cover_url: String? = null
)

// --- Books & Reading ---
@Keep
data class BookInfo(
    val id: Int,
    val title: String,
    val author: String,
    val total_chapters: Int,
    val series_name: String? = null,
    val series_number: Float? = null,
    val date_added: String,
    val cover_image: String?,
    val summary: String? = null
)

@Keep
data class ChapterData(
    val book_id: Int,
    val chapter_title: String,
    val chapter_index: Int,
    val text: String
)

@Keep
data class ProgressData(
    val current_chapter: Int,
    val scroll_progress: Float = 0f
)

@Keep
data class ProgressUpdateRequest(
    val chapter_index: Int,
    val scroll_progress: Float = 0f
)

@Keep
data class HighlightItem(
    val id: Int,
    val chapter_index: Int,
    val highlighted_text: String,
    val note: String?,
    val color: String?,
    val created_at: String
)

@Keep
data class HighlightRequest(
    val chapter_index: Int,
    val highlighted_text: String,
    val note: String? = null,
    val color: String
)
@Keep
data class LastReadInfo(
    val book_id: Int,
    val title: String,
    val total_chapters: Int,
    val chapter_index: Int,
    val scroll_progress: Float = 0f
)

// --- Requests & External API ---
@Keep
data class OpenLibraryBook(
    val book_id: String,
    val title: String,
    val author: String,
    val cover_url: String?,
    val publish_year: Int?
)

@Keep
data class BookRequestCreate(
    val open_library_id: String? = null,
    val title: String,
    val author: String,
    val cover_url: String?
)

@Keep
data class NewUserRequest(
    val username: String,
    val password: String,
    val is_admin: Boolean
)

@Keep
data class RequestItem(
    val id: Int,
    val title: String,
    val author: String,
    val open_library_id: String,
    val requestedBy: String,
    val status: String,
    val requestedAt: String
)