package com.poorMenKindle.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class LocalChapterToc(val chapterIndex: Int, val chapterTitle: String)

@Dao
interface BookDao {
    @Query("SELECT chapterIndex, chapterTitle FROM downloaded_chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getTocForBook(bookId: Int): List<LocalChapterToc>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: LocalBook)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<LocalChapter>)

    @Query("SELECT * FROM downloaded_books WHERE bookId = :bookId")
    suspend fun getDownloadedBook(bookId: Int): LocalBook?

    @Query("SELECT * FROM downloaded_chapters WHERE bookId = :bookId AND chapterIndex = :chapterIndex")
    suspend fun getChapter(bookId: Int, chapterIndex: Int): LocalChapter?

    @Query("DELETE FROM downloaded_books WHERE bookId = :bookId")
    suspend fun deleteBook(bookId: Int)

    @Query("DELETE FROM downloaded_chapters WHERE bookId = :bookId")
    suspend fun deleteChapters(bookId: Int)

    @Query("SELECT * FROM downloaded_books")
    suspend fun getAllDownloadedBooks(): List<LocalBook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: LocalHighlight): Long

    @Query("SELECT * FROM local_highlights WHERE bookId = :bookId")
    suspend fun getHighlightsForBook(bookId: Int): List<LocalHighlight>

    @Query("DELETE FROM local_highlights WHERE localId = :id")
    suspend fun deleteHighlight(id: Int)

    @Query("DELETE FROM local_highlights WHERE serverHighlightId = :serverId")
    suspend fun deleteHighlightByServerId(serverId: Int)

    @Query("UPDATE local_highlights SET note = :note WHERE localId = :id")
    suspend fun updateHighlightNote(id: Int, note: String?)

    @Query("UPDATE local_highlights SET note = :note WHERE serverHighlightId = :serverId")
    suspend fun updateHighlightNoteByServerId(serverId: Int, note: String?)

    @Query("UPDATE local_highlights SET serverHighlightId = :serverId WHERE localId = :localId")
    suspend fun updateHighlightServerId(localId: Int, serverId: Int)
}