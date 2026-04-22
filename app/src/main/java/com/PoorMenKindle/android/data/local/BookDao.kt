package com.PoorMenKindle.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookDao {
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
}